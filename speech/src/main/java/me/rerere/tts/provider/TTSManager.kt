package me.rerere.tts.provider

import android.content.Context
import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.kokoro.KokoroPackageManager
import me.rerere.tts.provider.providers.ElevenLabsTTSProvider
import me.rerere.tts.provider.providers.FishAudioTTSProvider
import me.rerere.tts.provider.providers.GeminiTTSProvider
import me.rerere.tts.provider.providers.GroqTTSProvider
import me.rerere.tts.provider.providers.MiMoTTSProvider
import me.rerere.tts.provider.providers.MiniMaxTTSProvider
import me.rerere.tts.provider.providers.KokoroLocalTTSProvider
import me.rerere.tts.provider.providers.OpenAITTSProvider
import me.rerere.tts.provider.providers.QwenTTSProvider
import me.rerere.tts.provider.providers.StepTTSProvider
import me.rerere.tts.provider.providers.SystemTTSProvider
import me.rerere.tts.provider.providers.XAITTSProvider

class TTSManager(
    private val context: Context,
    kokoroPackageManager: KokoroPackageManager,
) {
    private val openAIProvider = OpenAITTSProvider()
    private val geminiProvider = GeminiTTSProvider()
    private val systemProvider = SystemTTSProvider()
    private val kokoroProvider = KokoroLocalTTSProvider(kokoroPackageManager)
    private val miniMaxProvider = MiniMaxTTSProvider()
    private val qwenProvider = QwenTTSProvider()
    private val groqProvider = GroqTTSProvider()
    private val xaiProvider = XAITTSProvider()
    private val miMoProvider = MiMoTTSProvider()
    private val stepProvider = StepTTSProvider()
    private val elevenLabsProvider = ElevenLabsTTSProvider()
    private val fishAudioProvider = FishAudioTTSProvider()

    fun generateSpeech(
        providerSetting: TTSProviderSetting,
        request: TTSRequest
    ): Flow<AudioChunk> {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Gemini -> geminiProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.SystemTTS -> systemProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.LocalVoiceLibrary -> kokoroProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.KokoroLocal -> kokoroProvider.generateSpeech(
                context,
                providerSetting.toLocalVoiceLibrary(),
                request,
            )
            is TTSProviderSetting.MiniMax -> miniMaxProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Qwen -> qwenProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Groq -> groqProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.XAI -> xaiProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.MiMo -> miMoProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.FishAudio -> fishAudioProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Step -> stepProvider.generateSpeech(context, providerSetting, request)
        }
    }

    /**
     * 返回该 provider 硬编码的语气标记引导提示词（默认空）。
     * 供 text_to_speech 工具注入 system prompt 使用。
     */
    fun getPromptGuidance(providerSetting: TTSProviderSetting): String {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.promptGuidance
            is TTSProviderSetting.Gemini -> geminiProvider.promptGuidance
            is TTSProviderSetting.SystemTTS -> systemProvider.promptGuidance
            is TTSProviderSetting.LocalVoiceLibrary -> kokoroProvider.promptGuidance
            is TTSProviderSetting.KokoroLocal -> kokoroProvider.promptGuidance
            is TTSProviderSetting.MiniMax -> miniMaxProvider.promptGuidance
            is TTSProviderSetting.Qwen -> qwenProvider.promptGuidance
            is TTSProviderSetting.Groq -> groqProvider.promptGuidance
            is TTSProviderSetting.XAI -> xaiProvider.promptGuidance
            is TTSProviderSetting.MiMo -> miMoProvider.promptGuidance
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.promptGuidance
            is TTSProviderSetting.FishAudio -> fishAudioProvider.promptGuidance
            is TTSProviderSetting.Step -> stepProvider.promptGuidance
        }
    }

    private fun TTSProviderSetting.KokoroLocal.toLocalVoiceLibrary(): TTSProviderSetting.LocalVoiceLibrary {
        val engineId = if (packageId.contains("full", ignoreCase = true)) {
            LocalVoiceEngine.KOKORO_FULL.id
        } else {
            LocalVoiceEngine.KOKORO_INT8.id
        }
        return TTSProviderSetting.LocalVoiceLibrary(
            id = id,
            name = name.ifBlank { "Local Voice Library" },
            engineId = engineId,
            voiceId = voiceId,
            speechRate = speechRate,
            provider = "cpu",
        )
    }
}
