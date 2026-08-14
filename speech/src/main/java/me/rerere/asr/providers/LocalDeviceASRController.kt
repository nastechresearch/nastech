package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
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
import me.rerere.asr.LocalAsrModelPackage
import me.rerere.asr.LocalAsrPackageManager
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import java.io.File

/**
 * Live, fully on-device ASR for the verified Nastech Zipformer package.
 *
 * NNAPI is requested only when selected. If a device cannot initialize the selected model through
 * NNAPI, the controller recreates the recognizer on CPU and continues with the same model.
 */
class LocalDeviceASRController(
    private val context: Context,
    private val packageManager: LocalAsrPackageManager,
    private val provider: ASRProviderSetting.LocalDevice,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var finalTranscript = ""
    private var onTranscriptChange: ((String) -> Unit)? = null

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        _state.update { ASRState(status = ASRStatus.Connecting, isAvailable = true) }
        recorderJob = scope.launch(Dispatchers.IO) {
            try {
                val modelPackage = LocalAsrModelPackage.fromId(provider.modelId)
                val modelDirectory = packageManager.readyDirectory(modelPackage)
                    ?: error("Download the selected local speech model before recording")
                val runtime = createRuntime(modelDirectory)
                recognizer = runtime.recognizer
                stream = runtime.recognizer.createStream()
                finalTranscript = ""
                _state.update {
                    it.copy(
                        status = ASRStatus.Listening,
                        errorMessage = if (runtime.didFallback) "Android accelerator unavailable; using CPU optimized" else null,
                    )
                }
                recordLoop(runtime.recognizer, stream ?: error("Unable to create local recognition stream"))
            } catch (error: Throwable) {
                setError(error.message ?: "Local speech recognition could not start")
            } finally {
                releaseRuntime()
            }
        }
    }

    override fun stop() {
        _state.update { it.copy(status = ASRStatus.Stopping) }
        recorderJob?.cancel()
        recorderJob = null
        releaseRecorder()
        releaseRuntime()
        _state.update { it.copy(status = ASRStatus.Idle) }
    }

    override fun dispose() {
        stop()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordLoop(recognizer: OnlineRecognizer, stream: OnlineStream) {
        val minBuffer = AudioRecord.getMinBufferSize(
            provider.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = minBuffer.coerceAtLeast(provider.sampleRate / 10 * BYTES_PER_SAMPLE).coerceAtLeast(MIN_BUFFER_BYTES)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            provider.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2,
        )
        audioRecord = recorder
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Unable to initialize the microphone for local recognition" }
            recorder.startRecording()
            val buffer = ByteArray(bufferSize)
            while (currentCoroutineContext().isActive && audioRecord === recorder) {
                val read = recorder.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> processFrame(recognizer, stream, buffer, read)
                    read < 0 -> error("Microphone read error: $read")
                }
            }
        } finally {
            releaseRecorder()
        }
    }

    private fun processFrame(
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        buffer: ByteArray,
        bytesRead: Int,
    ) {
        val samples = pcm16ToFloats(buffer, bytesRead)
        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(calculateRmsAmplitude(buffer, bytesRead))) }
        stream.acceptWaveform(samples, provider.sampleRate)
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        publishPartial(recognizer.getResult(stream).text)
        if (recognizer.isEndpoint(stream)) {
            val endpoint = recognizer.getResult(stream).text.trim()
            if (endpoint.isNotEmpty()) {
                finalTranscript = listOf(finalTranscript, endpoint).filter(String::isNotBlank).joinToString(" ")
            }
            recognizer.reset(stream)
            publishPartial("")
        }
    }

    private fun publishPartial(partial: String) {
        val transcript = listOf(finalTranscript, partial.trim()).filter(String::isNotBlank).joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch { onTranscriptChange?.invoke(transcript) }
    }

    private fun createRuntime(modelDirectory: File): LocalRuntime {
        val requestedProvider = provider.provider.normalizedProvider()
        return runCatching { LocalRuntime(createRecognizer(modelDirectory, requestedProvider), didFallback = false) }
            .getOrElse { original ->
                if (requestedProvider != NNAPI_PROVIDER) throw original
                LocalRuntime(createRecognizer(modelDirectory, CPU_PROVIDER), didFallback = true)
            }
    }

    private fun createRecognizer(modelDirectory: File, runtimeProvider: String): OnlineRecognizer {
        val modelPackage = LocalAsrModelPackage.fromId(provider.modelId)
        return OnlineRecognizer(
            assetManager = null,
            config = OnlineRecognizerConfig(
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = modelDirectory.resolve(modelPackage.encoderFile).absolutePath,
                        decoder = modelDirectory.resolve(modelPackage.decoderFile).absolutePath,
                        joiner = modelDirectory.resolve(modelPackage.joinerFile).absolutePath,
                    ),
                    tokens = modelDirectory.resolve("tokens.txt").absolutePath,
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_CPU_THREADS),
                    provider = runtimeProvider,
                    modelType = "zipformer",
                ),
                enableEndpoint = true,
            ),
        )
    }

    private fun releaseRuntime() {
        runCatching { stream?.release() }
        stream = null
        runCatching { recognizer?.release() }
        recognizer = null
    }

    private fun releaseRecorder() {
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun setError(message: String) {
        _state.update { it.copy(status = ASRStatus.Error, errorMessage = message) }
    }

    private fun String.normalizedProvider(): String = when (lowercase()) {
        NNAPI_PROVIDER -> NNAPI_PROVIDER
        else -> CPU_PROVIDER
    }

    private fun pcm16ToFloats(bytes: ByteArray, size: Int): FloatArray {
        val samples = FloatArray(size / BYTES_PER_SAMPLE)
        var byteIndex = 0
        var sampleIndex = 0
        while (byteIndex + 1 < size) {
            val low = bytes[byteIndex].toInt() and 0xff
            val high = bytes[byteIndex + 1].toInt()
            val value = (high shl 8) or low
            samples[sampleIndex++] = value / 32768f
            byteIndex += BYTES_PER_SAMPLE
        }
        return samples
    }

    private data class LocalRuntime(
        val recognizer: OnlineRecognizer,
        val didFallback: Boolean,
    )

    private companion object {
        const val CPU_PROVIDER = "cpu"
        const val NNAPI_PROVIDER = "nnapi"
        const val MAX_CPU_THREADS = 4
        const val BYTES_PER_SAMPLE = 2
        const val MIN_BUFFER_BYTES = 4096
    }
}
