package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections

private const val TAG = "ElevenLabsASR"
private const val MAX_SEGMENT_BYTES = 6 * 1024 * 1024

/**
 * Buffered cloud transcription for ElevenLabs Scribe.
 *
 * AudioRecord produces PCM16 bytes, while the ElevenLabs speech-to-text endpoint
 * accepts an audio file. Each recorded segment is wrapped in a minimal WAV container
 * and sent as multipart/form-data to `/v1/speech-to-text`. The controller never
 * stores audio after a segment has been successfully sent.
 */
class ElevenLabsASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.ElevenLabs,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var flushJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()
    private var segmentStartElapsedMs = 0L
    private val completedTranscripts = Collections.synchronizedList(mutableListOf<String>())

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setError("Microphone permission is required")
            return
        }
        if (provider.apiKey.isBlank()) {
            setError("Add an ElevenLabs API key before starting transcription")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
        }
        completedTranscripts.clear()
        _state.value = ASRState(status = ASRStatus.Listening, isAvailable = true)
        startRecorder()
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        _state.update { it.copy(status = ASRStatus.Stopping) }
        scope.launch(Dispatchers.IO) {
            try {
                flushJob?.join()
                flushSegment()
                _state.update { it.copy(status = ASRStatus.Idle) }
            } catch (error: Exception) {
                Log.e(TAG, "Final ElevenLabs transcription upload failed", error)
                setError(error.message ?: "ElevenLabs transcription failed")
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        flushJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
            )
            audioRecord = recorder

            try {
                recorder.startRecording()
                val buffer = ByteArray(minBufferSize)
                val segmentLengthMs = provider.segmentDurationSec.coerceAtLeast(0) * 1_000L
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    when {
                        read > 0 -> {
                            val shouldFlush = synchronized(bufferLock) {
                                currentBuffer.write(buffer, 0, read)
                                val elapsed = SystemClock.elapsedRealtime() - segmentStartElapsedMs
                                currentBuffer.size() >= MAX_SEGMENT_BYTES ||
                                    (segmentLengthMs > 0 && elapsed >= segmentLengthMs)
                            }
                            _state.update {
                                it.copy(amplitudes = it.amplitudes.appendAmplitude(calculateRmsAmplitude(buffer, read)))
                            }
                            if (shouldFlush) triggerFlush()
                        }

                        read < 0 -> throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Audio recording failed", error)
                setError(error.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun triggerFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            runCatching { flushSegment() }
                .onFailure { error ->
                    Log.e(TAG, "ElevenLabs transcription segment failed", error)
                    setError(error.message ?: "ElevenLabs transcription failed")
                }
        }
    }

    private fun flushSegment() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size() == 0) return
            currentBuffer.toByteArray().also {
                currentBuffer = ByteArrayOutputStream()
                segmentStartElapsedMs = SystemClock.elapsedRealtime()
            }
        }
        val wavBytes = pcm16ToWav(pcmBytes, provider.sampleRate)
        val audioBody = wavBytes.asRequestBody("audio/wav".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "nastech-recording.wav", audioBody)
            .addFormDataPart("model_id", provider.model)
            .apply {
                provider.language.trim().takeIf { it.isNotEmpty() }?.let {
                    addFormDataPart("language_code", it)
                }
            }
            .build()

        val request = Request.Builder()
            .url("${provider.baseUrl.trimEnd('/')}/v1/speech-to-text")
            .addHeader("xi-api-key", provider.apiKey.trim())
            .post(multipart)
            .build()

        val transcript = httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("ElevenLabs STT HTTP ${response.code}: $body")
            }
            parseTranscript(body)
        }
        if (transcript.isNotBlank()) {
            completedTranscripts.add(transcript)
            publishTranscript()
        }
    }

    private fun parseTranscript(body: String): String = runCatching {
        JSONObject(body).optString("text").trim()
    }.getOrElse { error ->
        throw IOException("ElevenLabs STT response is not valid JSON", error)
    }

    private fun publishTranscript() {
        val transcript = completedTranscripts.filter { it.isNotBlank() }.joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch { onTranscriptChange?.invoke(transcript) }
    }

    private fun setError(message: String) {
        _state.update { it.copy(status = ASRStatus.Error, errorMessage = message) }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun pcm16ToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val out = ByteArrayOutputStream(44 + pcm.size)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 36 + pcm.size)
        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 16)
        writeShortLE(out, 1)
        writeShortLE(out, channels)
        writeIntLE(out, sampleRate)
        writeIntLE(out, byteRate)
        writeShortLE(out, blockAlign)
        writeShortLE(out, bitsPerSample)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }

    private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}
