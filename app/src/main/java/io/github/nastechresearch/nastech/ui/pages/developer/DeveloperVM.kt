package io.github.nastechresearch.nastech.ui.pages.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import io.github.nastechresearch.nastech.data.ai.AILoggingManager
import io.github.nastechresearch.nastech.data.datastore.AiLogLevel

class DeveloperVM(
    private val aiLoggingManager: AILoggingManager,
) : ViewModel() {
    val logs = aiLoggingManager.getLogs()
    val logLevel = aiLoggingManager.getLogLevel()

    fun setLogLevel(level: AiLogLevel) {
        viewModelScope.launch {
            aiLoggingManager.setLogLevel(level)
        }
    }
}
