package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
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
    private val onClientCommand: (String) -> String = {
        "No active Nastech chat is available to receive this voice command."
    },
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
    private val alignmentJobs = mutableListOf<Job>()
    private var alignmentGeneration = 0L
    private var onTranscriptChange: ((String) -> Unit)? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode: Int? = null
    private var communicationDeviceSelected = false
    private var callAudioFocusRequest: AudioFocusRequest? = null
    private var inputSampleRate = provider.sampleRate.coerceAtLeast(8_000)
    private var outputSampleRate = provider.sampleRate.coerceAtLeast(8_000)

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
        acquireSpeakerRoute()
        setLiveCallActive(true)
        _state.value = ASRState(status = ASRStatus.Connecting, isAvailable = true)
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
        cancelSpeechAlignment(resetProgress = true)
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
        cancelSpeechAlignment(resetProgress = true)
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
                // Start capture and playback only after the server confirms its PCM
                // formats. This creates the explicit session-ready transition used by
                // Happy's realtime bridge and prevents format races.
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
                inputSampleRate = pcmSampleRate(metadata?.optString("user_input_audio_format"))
                    ?: provider.sampleRate.coerceAtLeast(8_000)
                outputSampleRate = pcmSampleRate(metadata?.optString("agent_output_audio_format"))
                    ?: provider.sampleRate.coerceAtLeast(8_000)
                startPlayback(generation)
                _state.update {
                    it.copy(
                        status = ASRStatus.Listening,
                        isAvailable = true,
                        errorMessage = null,
                    )
                }
                startRecorder(generation)
            }

            "audio" -> {
                val audioEvent = event.optJSONObject("audio_event")
                val encoded = audioEvent?.optString("audio_base_64").orEmpty()
                if (encoded.isNotBlank()) {
                    val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        _state.update { it.copy(isAgentSpeaking = true) }
                        audioQueue?.trySend(bytes)
                    }
                }
                audioEvent?.optJSONObject("alignment")?.let { alignment ->
                    scheduleSpeechAlignment(generation, alignment)
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
                    cancelSpeechAlignment(resetProgress = false)
                    _state.update {
                        it.copy(
                            agentResponse = response,
                            agentSpeechProgressChars = 0,
                            errorMessage = null,
                        )
                    }
                }
            }

            "client_tool_call" -> handleClientToolCall(generation, event)

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
                val ping = event.optJSONObject("ping_event") ?: return
                val eventId = ping.optLong("event_id", -1L)
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

            "agent_response_complete" -> {
                _state.update {
                    it.copy(
                        isAgentSpeaking = false,
                        agentSpeechProgressChars = it.agentResponse.length,
                    )
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
            val sampleRate = inputSampleRate
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
            val sampleRate = outputSampleRate
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
            track.setVolume(1f)
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

    private fun handleClientToolCall(generation: Long, event: JSONObject) {
        val call = event.optJSONObject("client_tool_call") ?: return
        val toolName = call.optString("tool_name").trim()
        val toolCallId = call.optString("tool_call_id").trim()
        if (toolCallId.isBlank()) return
        val command = call.optJSONObject("parameters")
            ?.optString(provider.commandParameterName)
            .orEmpty()
            .trim()
        scope.launch {
            val isCommandTool = toolName == provider.commandToolName
            val result = when {
                !isCommandTool -> "Nastech does not expose the requested client tool: $toolName"
                command.isBlank() -> "Missing required ${provider.commandParameterName} parameter"
                else -> runCatching { onClientCommand(command) }
                    .getOrElse { error -> "Nastech command dispatch failed: ${error.message}" }
            }
            if (isCurrent(generation)) {
                webSocket?.send(
                    JSONObject()
                        .put("type", "client_tool_result")
                        .put("tool_call_id", toolCallId)
                        .put("result", result)
                        .put("is_error", !isCommandTool || command.isBlank())
                        .toString(),
                )
            }
        }
    }

    /**
     * Reveals a response only as its PCM audio reaches the listener. ElevenLabs
     * timestamps are relative to each matching audio event, so progress updates use
     * incremental delays and cannot move backwards.
     */
    private fun scheduleSpeechAlignment(generation: Long, alignment: JSONObject) {
        val chars = alignment.optJSONArray("chars") ?: return
        val startTimes = alignment.optJSONArray("char_start_times_ms") ?: return
        val count = minOf(chars.length(), startTimes.length())
        if (count <= 0) return

        val alignedText = buildString {
            repeat(count) { index -> append(chars.optString(index)) }
        }
        val response = state.value.agentResponse
        if (response.isBlank() || alignedText.isBlank()) return

        val currentProgress = state.value.agentSpeechProgressChars.coerceIn(0, response.length)
        val responseOffset = response.indexOf(alignedText, startIndex = currentProgress)
            .takeIf { it >= 0 }
            ?: currentProgress
        val expectedAlignment = alignmentGeneration
        val job = scope.launch {
            var previousTimeMs = 0L
            repeat(count) { index ->
                val startTimeMs = startTimes.optLong(index, previousTimeMs).coerceAtLeast(previousTimeMs)
                delay(startTimeMs - previousTimeMs)
                previousTimeMs = startTimeMs
                if (!isCurrent(generation) || expectedAlignment != alignmentGeneration) return@launch
                _state.update { current ->
                    val progress = (responseOffset + index + 1).coerceAtMost(current.agentResponse.length)
                    current.copy(agentSpeechProgressChars = maxOf(current.agentSpeechProgressChars, progress))
                }
            }
        }
        alignmentJobs += job
    }

    private fun cancelSpeechAlignment(resetProgress: Boolean) {
        alignmentGeneration += 1
        alignmentJobs.forEach { it.cancel() }
        alignmentJobs.clear()
        if (resetProgress) {
            _state.update { it.copy(agentSpeechProgressChars = 0) }
        }
    }

    private fun pcmSampleRate(format: String?): Int? {
        val match = Regex("pcm_(\\d+)").matchEntire(format.orEmpty().lowercase()) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it in 8_000..48_000 }
    }

    @Suppress("DEPRECATION")
    private fun acquireSpeakerRoute() {
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        callAudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        callAudioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            communicationDeviceSelected = speaker != null && audioManager.setCommunicationDevice(speaker)
        } else {
            audioManager.isSpeakerphoneOn = true
            communicationDeviceSelected = true
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseSpeakerRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (communicationDeviceSelected) audioManager.clearCommunicationDevice()
        } else if (communicationDeviceSelected) {
            audioManager.isSpeakerphoneOn = false
        }
        communicationDeviceSelected = false
        callAudioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        callAudioFocusRequest = null
        previousAudioMode?.let { audioManager.mode = it }
        previousAudioMode = null
    }

    private fun flushAgentAudio() {
        cancelSpeechAlignment(resetProgress = true)
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
        cancelSpeechAlignment(resetProgress = true)
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
        cancelSpeechAlignment(resetProgress = true)
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
        if (!active) releaseSpeakerRoute()
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
