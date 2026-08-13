package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for a user-operated [Kokoro-FastAPI](https://github.com/remsky/Kokoro-FastAPI)
 * server. The service accepts the standard OpenAI `audio/speech` request contract, so no
 * model weights or inference runtime are bundled with the Android application.
 */
class KokoroTTSProvider : TTSProvider<TTSProviderSetting.KokoroFastAPI> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.KokoroFastAPI,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val requestBody = JSONObject().apply {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("response_format", providerSetting.responseFormat)
        }

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl.trimEnd('/')}/audio/speech")
            .addHeader("Content-Type", "application/json")
            .apply {
                providerSetting.apiKey.trim().takeIf(String::isNotEmpty)?.let { token ->
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val audioData = httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body.string().take(512)
                error("Kokoro TTS request failed: ${response.code} ${response.message} $details")
            }
            response.body.bytes()
        }

        emit(
            AudioChunk(
                data = audioData,
                format = providerSetting.responseFormat.toAudioFormat(),
                isLast = true,
                metadata = mapOf(
                    "provider" to "kokoro-fastapi",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice,
                ),
            ),
        )
    }

    private fun String.toAudioFormat(): AudioFormat = when (lowercase()) {
        "wav" -> AudioFormat.WAV
        "ogg" -> AudioFormat.OGG
        "aac" -> AudioFormat.AAC
        "opus" -> AudioFormat.OPUS
        "pcm" -> AudioFormat.PCM
        else -> AudioFormat.MP3
    }
}
