package me.rerere.asr

import kotlinx.coroutines.flow.StateFlow

interface ASRController {
    val state: StateFlow<ASRState>

    /** True when this controller owns a full duplex agent call rather than dictation. */
    val isLiveConversation: Boolean
        get() = false

    fun start(onTranscriptChange: (String) -> Unit)
    fun stop()
    fun dispose()
}
