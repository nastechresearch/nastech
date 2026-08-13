package me.rerere.tts.provider.providers

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.kokoro.KokoroModelPackage
import me.rerere.tts.kokoro.KokoroPackageManager
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

/**
 * On-device Kokoro synthesis backed by the complete Sherpa-ONNX package. The provider deliberately
 * refuses to run until [KokoroPackageManager] has verified and unpacked the model in app storage.
 */
class KokoroLocalTTSProvider(
    private val packageManager: KokoroPackageManager,
) : TTSProvider<TTSProviderSetting.KokoroLocal> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.KokoroLocal,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val modelDirectory = packageManager.readyDirectory()
            ?: error("Download and verify the Kokoro local voice package before using this provider")
        if (providerSetting.packageId != KokoroModelPackage.ID) {
            error("The selected Kokoro package is not available on this device")
        }

        val kokoro = OfflineTtsKokoroModelConfig().apply {
            model = modelDirectory.resolve("model.onnx").absolutePath
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
            numThreads = 2
            debug = false
            provider = "cpu"
        }
        val config = OfflineTtsConfig().apply {
            model = modelConfig
            maxNumSentences = 1
            silenceScale = 0.2f
        }

        // The resolved Android AAR accepts AssetManager even when all model paths point to
        // app-owned files; it uses it to load the bundled native runtime.
        val tts = OfflineTts(context.assets, config)
        try {
            val audio = tts.generate(
                request.text,
                KokoroModelPackage.speakerId(providerSetting.voiceId),
                providerSetting.speechRate.coerceIn(0.5f, 2.0f),
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
                        "voiceId" to providerSetting.voiceId,
                        "modelVersion" to providerSetting.modelVersion,
                        "onDevice" to "true",
                    ),
                ),
            )
        } finally {
            tts.release()
        }
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

    private companion object {
        const val WAV_HEADER_BYTES = 44
        const val CHANNELS = 1
        const val PCM_BYTES_PER_SAMPLE = 2
        const val BITS_PER_SAMPLE = 16
    }
}
