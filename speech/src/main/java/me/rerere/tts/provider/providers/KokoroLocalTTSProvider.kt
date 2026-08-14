package me.rerere.tts.provider.providers

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.kokoro.KokoroModelPackage
import me.rerere.tts.kokoro.KokoroPackageManager
import me.rerere.tts.kokoro.KokoroPackageVariant
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.LocalVoiceEngine
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

/**
 * On-device Kokoro v1.1 synthesis backed by a checksum-verified, app-private model package.
 * Every local package contains the same 103 speaker embeddings. The optional NNAPI preference asks
 * Android to use a compatible accelerator and transparently recreates the runtime on CPU if setup
 * fails on the current device.
 */
class KokoroLocalTTSProvider(
    private val packageManager: KokoroPackageManager,
) : TTSProvider<TTSProviderSetting.LocalVoiceLibrary> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.LocalVoiceLibrary,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val modelPackage = LocalVoiceEngine.fromId(providerSetting.engineId).modelPackage
        val modelDirectory = packageManager.readyDirectory(modelPackage)
            ?: error("Download and verify the selected local voice package before using this provider")
        val requestedProvider = providerSetting.provider.normalizedProvider()
        val runtime = createRuntime(context, modelDirectory, modelPackage, requestedProvider)
        try {
            val audio = runtime.tts.generate(
                request.text,
                KokoroModelPackage.speakerId(providerSetting.voiceId),
                providerSetting.speechRate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
            )
            val samples = audio.samples
            if (samples.isEmpty()) error("Kokoro returned no audio for this text")
            emit(
                AudioChunk(
                    data = pcmFloatsToWav(samples, audio.sampleRate),
                    format = AudioFormat.WAV,
                    sampleRate = audio.sampleRate,
                    isLast = true,
                    metadata = mapOf(
                        "provider" to "kokoro-local",
                        "requestedProvider" to requestedProvider,
                        "activeProvider" to runtime.activeProvider,
                        "acceleratorFallback" to runtime.didFallback.toString(),
                        "voiceId" to KokoroModelPackage.voiceFor(providerSetting.voiceId).id,
                        "speakerId" to KokoroModelPackage.speakerId(providerSetting.voiceId).toString(),
                        "packageId" to modelPackage.id,
                        "modelVersion" to KokoroModelPackage.VERSION,
                        "onDevice" to "true",
                    ),
                ),
            )
        } finally {
            runtime.tts.release()
        }
    }

    private fun createRuntime(
        context: Context,
        modelDirectory: File,
        modelPackage: KokoroPackageVariant,
        requestedProvider: String,
    ): RuntimeSelection {
        return runCatching {
            RuntimeSelection(
                tts = createOfflineTts(context, modelDirectory, modelPackage, requestedProvider),
                activeProvider = requestedProvider,
                didFallback = false,
            )
        }.getOrElse { originalError ->
            if (requestedProvider != NNAPI_PROVIDER) throw originalError
            RuntimeSelection(
                tts = createOfflineTts(context, modelDirectory, modelPackage, CPU_PROVIDER),
                activeProvider = CPU_PROVIDER,
                didFallback = true,
            )
        }
    }

    private fun createOfflineTts(
        context: Context,
        modelDirectory: File,
        modelPackage: KokoroPackageVariant,
        provider: String,
    ): OfflineTts {
        val kokoro = OfflineTtsKokoroModelConfig().apply {
            model = modelDirectory.resolve(modelPackage.modelFileName).absolutePath
            voices = modelDirectory.resolve("voices.bin").absolutePath
            tokens = modelDirectory.resolve("tokens.txt").absolutePath
            dataDir = modelDirectory.resolve("espeak-ng-data").absolutePath
            lexicon = listOf(
                modelDirectory.resolve("lexicon-us-en.txt").absolutePath,
                modelDirectory.resolve("lexicon-zh.txt").absolutePath,
            ).joinToString(",")
        }
        val modelConfig = OfflineTtsModelConfig().apply {
            this.kokoro = kokoro
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_CPU_THREADS)
            debug = false
            this.provider = provider
        }
        val config = OfflineTtsConfig().apply {
            model = modelConfig
            maxNumSentences = 1
            silenceScale = 0.2f
        }
        return OfflineTts(context.assets, config)
    }

    private fun pcmFloatsToWav(samples: FloatArray, sampleRate: Int): ByteArray {
        val pcmBytes = samples.size * PCM_BYTES_PER_SAMPLE
        val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".encodeToByteArray())
        header.putInt(WAV_HEADER_BYTES - 8 + pcmBytes)
        header.put("WAVEfmt ".encodeToByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(CHANNELS.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * CHANNELS * PCM_BYTES_PER_SAMPLE)
        header.putShort((CHANNELS * PCM_BYTES_PER_SAMPLE).toShort())
        header.putShort(BITS_PER_SAMPLE.toShort())
        header.put("data".encodeToByteArray())
        header.putInt(pcmBytes)

        val output = ByteArrayOutputStream(WAV_HEADER_BYTES + pcmBytes)
        output.write(header.array())
        val pcm = ByteBuffer.allocate(PCM_BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { sample ->
            pcm.clear()
            pcm.putShort((sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            output.write(pcm.array())
        }
        return output.toByteArray()
    }

    private data class RuntimeSelection(
        val tts: OfflineTts,
        val activeProvider: String,
        val didFallback: Boolean,
    )

    private fun String.normalizedProvider(): String = when (lowercase()) {
        NNAPI_PROVIDER -> NNAPI_PROVIDER
        else -> CPU_PROVIDER
    }

    private companion object {
        const val CPU_PROVIDER = "cpu"
        const val NNAPI_PROVIDER = "nnapi"
        const val WAV_HEADER_BYTES = 44
        const val CHANNELS = 1
        const val PCM_BYTES_PER_SAMPLE = 2
        const val BITS_PER_SAMPLE = 16
        const val MAX_CPU_THREADS = 4
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
    }
}
