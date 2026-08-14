package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.asr.LiveVoiceCallRegistry
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

private const val TAG = "ElevenLabsSTS"
private const val PCM16_BIT_DEPTH = 16
private const val PCM_CHANNELS = 1

/**
 * A full-duplex ElevenLabs Agents call backed by the official conversational
 * WebSocket protocol. Audio is kept in memory only: PCM16 microphone chunks
 * flow directly to the socket and agent PCM16 chunks flow directly to an
 * AudioTrack playback queue.
 */
class ElevenLabsSTSController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.ElevenLabsSTS,
    private val onLiveCallStateChanged: (Boolean) -> Unit = {},
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()
    override val isLiveConversation: Boolean = true

    @Volatile
    private var sessionGeneration = 0L

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var webSocket: WebSocket? = null
    private var recorderJob: Job? = null
    private var playbackJob: Job? = null
    private var audioQueue: Channel<ByteArray>? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setError("Microphone permission is required")
            return
        }
        if (provider.apiKey.isBlank()) {
            setError("Add an ElevenLabs API key before starting a live agent call")
            return
        }
        if (provider.agentId.isBlank()) {
            setError("Add an ElevenLabs agent ID before starting a live agent call")
            return
        }

        val generation = ++sessionGeneration
        this.onTranscriptChange = onTranscriptChange
        setLiveCallActive(true)
        _state.value = ASRState(status = ASRStatus.Connecting, isAvailable = true)
        startPlayback(generation)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val signedUrl = requestSignedConversationUrl()
                if (isCurrent(generation)) connect(signedUrl, generation)
            }.onFailure { error ->
                if (isCurrent(generation)) {
                    Log.e(TAG, "Failed to connect live agent call", error)
                    finishWithError(generation, error.message ?: "ElevenLabs live agent call could not connect")
                }
            }
        }
    }

    override fun stop() {
        val generation = sessionGeneration
        if (!state.value.isRecording) return
        _state.update { it.copy(status = ASRStatus.Stopping, isAgentSpeaking = false) }
        sessionGeneration += 1
        webSocket?.close(1000, "Live call ended")
        webSocket = null
        releaseRecorder()
        releasePlayback()
        onTranscriptChange = null
        setLiveCallActive(false)
        if (generation >= 0) {
            _state.value = ASRState(status = ASRStatus.Idle, isAvailable = true)
        }
    }

    override fun dispose() {
        sessionGeneration += 1
        webSocket?.cancel()
        webSocket = null
        releaseRecorder()
        releasePlayback()
        onTranscriptChange = null
        setLiveCallActive(false)
        scope.cancel()
    }

    private fun requestSignedConversationUrl(): String {
        val encodedAgentId = URLEncoder.encode(provider.agentId.trim(), Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("${provider.baseUrl.trimEnd('/')}/v1/convai/conversation/get-signed-url?agent_id=$encodedAgentId")
            .addHeader("xi-api-key", provider.apiKey.trim())
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("ElevenLabs agent authorization HTTP ${response.code}: $body")
            }
            JSONObject(body).optString("signed_url").trim().takeIf { it.isNotEmpty() }
                ?: throw IOException("ElevenLabs did not return a signed conversation URL")
        }
    }

    private fun connect(signedUrl: String, generation: Long) {
        if (!isCurrent(generation)) return
        val request = Request.Builder().url(signedUrl).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(generation)) {
                    webSocket.close(1000, "Superseded call")
                    return
                }
                this@ElevenLabsSTSController.webSocket = webSocket
                val initialization = JSONObject()
                    .put("type", "conversation_initiation_client_data")
                if (!webSocket.send(initialization.toString())) {
                    finishWithError(generation, "ElevenLabs live agent call could not initialize")
                    return
                }
                _state.update {
                    it.copy(
                        status = ASRStatus.Listening,
                        isAvailable = true,
                        errorMessage = null,
                    )
                }
                startRecorder(generation)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(generation)) return
                handleSocketEvent(generation, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isCurrent(generation)) {
                    finishNormally(generation)
                }
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                if (isCurrent(generation)) {
                    Log.e(TAG, "ElevenLabs live agent socket failed", throwable)
                    finishWithError(
                        generation,
                        throwable.message ?: "ElevenLabs live agent call disconnected",
                    )
                }
            }
        })
    }

    private fun handleSocketEvent(generation: Long, rawEvent: String) {
        val event = runCatching { JSONObject(rawEvent) }.getOrElse { error ->
            Log.w(TAG, "Ignoring invalid ElevenLabs event", error)
            return
        }
        when (event.optString("type")) {
            "conversation_initiation_metadata" -> {
                val metadata = event.optJSONObject("conversation_initiation_metadata_event")
                val inputFormat = metadata?.optString("user_input_audio_format")
                val outputFormat = metadata?.optString("agent_output_audio_format")
                if (inputFormat != "pcm_16000" || outputFormat != "pcm_16000") {
                    finishWithError(
                        generation,
                        "This ElevenLabs agent must use PCM 16 kHz input and output audio",
                    )
                }
            }

            "audio" -> {
                val encoded = event.optJSONObject("audio_event")
                    ?.optString("audio_base_64")
                    .orEmpty()
                if (encoded.isNotBlank()) {
                    val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        _state.update { it.copy(isAgentSpeaking = true) }
                        audioQueue?.trySend(bytes)
                    }
                }
            }

            "user_transcript" -> {
                val transcript = event.optJSONObject("user_transcription_event")
                    ?.optString("user_transcript")
                    .orEmpty()
                    .trim()
                if (transcript.isNotBlank()) {
                    _state.update { it.copy(transcript = transcript, errorMessage = null) }
                    scope.launch { onTranscriptChange?.invoke(transcript) }
                }
            }

            "agent_response" -> {
                val response = event.optJSONObject("agent_response_event")
                    ?.optString("agent_response")
                    .orEmpty()
                    .trim()
                if (response.isNotBlank()) {
                    _state.update { it.copy(agentResponse = response, errorMessage = null) }
                }
            }

            "interruption" -> {
                flushAgentAudio()
            }

            "vad_score" -> {
                val score = event.optJSONObject("vad_score_event")
                    ?.optDouble("vad_score", 0.0)
                    ?.toFloat()
                    ?: 0f
                if (score >= provider.vadThreshold && state.value.isAgentSpeaking) {
                    flushAgentAudio()
                }
            }

            "ping" -> {
                val ping = event.optJSONObject("ping_event")
                val eventId = ping?.optLong("event_id", -1L) ?: -1L
                if (eventId >= 0L) {
                    val delayMs = ping.optLong("ping_ms", 0L).coerceAtLeast(0L)
                    scope.launch {
                        delay(delayMs)
                        if (isCurrent(generation)) {
                            webSocket?.send(
                                JSONObject()
                                    .put("type", "pong")
                                    .put("event_id", eventId)
                                    .toString(),
                            )
                        }
                    }
                }
            }

            "error" -> {
                val message = event.optJSONObject("error_event")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: event.optString("message").takeIf { it.isNotBlank() }
                    ?: "ElevenLabs live agent call returned an error"
                finishWithError(generation, message)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder(generation: Long) {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate.coerceAtLeast(8_000)
            val chunkDurationMs = provider.audioChunkDurationMs.coerceIn(10, 100)
            val chunkBytes = (sampleRate * PCM_CHANNELS * (PCM16_BIT_DEPTH / 8) * chunkDurationMs / 1_000)
                .coerceAtLeast(320)
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(chunkBytes * 2)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
            )
            audioRecord = recorder

            try {
                recorder.startRecording()
                val buffer = ByteArray(chunkBytes)
                while (isActive && isCurrent(generation)) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    when {
                        read > 0 -> {
                            val amplitude = calculateRmsAmplitude(buffer, read)
                            _state.update {
                                it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude))
                            }
                            if (amplitude >= provider.vadThreshold && state.value.isAgentSpeaking) {
                                flushAgentAudio()
                            }
                            val encoded = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                            val sent = webSocket?.send(
                                JSONObject().put("user_audio_chunk", encoded).toString(),
                            ) == true
                            if (!sent && isCurrent(generation)) {
                                throw IOException("ElevenLabs live agent audio stream disconnected")
                            }
                        }

                        read < 0 -> throw IOException("AudioRecord read error: $read")
                    }
                }
            } catch (error: Exception) {
                if (isCurrent(generation)) {
                    Log.e(TAG, "Live agent microphone stream failed", error)
                    finishWithError(generation, error.message ?: "Live agent microphone stream failed")
                }
            } finally {
                if (audioRecord === recorder) releaseRecorder()
            }
        }
    }

    private fun startPlayback(generation: Long) {
        releasePlayback()
        val queue = Channel<ByteArray>(Channel.UNLIMITED)
        audioQueue = queue
        playbackJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate.coerceAtLeast(8_000)
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(sampleRate * PCM_CHANNELS * (PCM16_BIT_DEPTH / 8) / 5)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = track
            track.play()
            try {
                for (chunk in queue) {
                    if (!isCurrent(generation)) break
                    track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                }
            } finally {
                if (audioTrack === track) {
                    runCatching { track.pause() }
                    runCatching { track.flush() }
                    runCatching { track.release() }
                    audioTrack = null
                }
            }
        }
    }

    private fun flushAgentAudio() {
        val queue = audioQueue
        while (queue?.tryReceive()?.isSuccess == true) {
            // Drain queued PCM immediately when either party interrupts.
        }
        audioTrack?.let { track ->
            runCatching {
                track.pause()
                track.flush()
                track.play()
            }
        }
        _state.update { it.copy(isAgentSpeaking = false) }
    }

    private fun finishNormally(generation: Long) {
        if (!isCurrent(generation)) return
        sessionGeneration += 1
        webSocket = null
        releaseRecorder()
        releasePlayback()
        onTranscriptChange = null
        setLiveCallActive(false)
        _state.value = ASRState(status = ASRStatus.Idle, isAvailable = true)
    }

    private fun finishWithError(generation: Long, message: String) {
        if (!isCurrent(generation)) return
        sessionGeneration += 1
        webSocket?.cancel()
        webSocket = null
        releaseRecorder()
        releasePlayback()
        onTranscriptChange = null
        setLiveCallActive(false)
        _state.value = ASRState(
            status = ASRStatus.Error,
            isAvailable = true,
            errorMessage = message,
        )
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                isAvailable = true,
                isAgentSpeaking = false,
                errorMessage = message,
            )
        }
    }

    private fun isCurrent(generation: Long): Boolean = generation == sessionGeneration

    private fun setLiveCallActive(active: Boolean) {
        LiveVoiceCallRegistry.isActive = active
        LiveVoiceCallRegistry.registerEndCallHandler(
            if (active) {
                { stop() }
            } else {
                null
            },
        )
        onLiveCallStateChanged(active)
    }

    private fun releaseRecorder() {
        recorderJob?.cancel()
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun releasePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        audioQueue?.close()
        audioQueue = null
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }
}
