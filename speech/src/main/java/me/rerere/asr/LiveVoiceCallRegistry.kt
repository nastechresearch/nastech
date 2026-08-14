package me.rerere.asr

/**
 * Coordinates exclusive live-call audio ownership within the speech module.
 * The active ElevenLabs STS controller plays agent PCM itself, so normal TTS
 * must not synthesize a duplicate response while that call remains active.
 */
object LiveVoiceCallRegistry {
    @Volatile
    var isActive: Boolean = false

    @Volatile
    private var endCallHandler: (() -> Unit)? = null

    fun registerEndCallHandler(handler: (() -> Unit)?) {
        endCallHandler = handler
    }

    fun endActiveCall() {
        endCallHandler?.invoke()
    }
}
