package io.github.nastechresearch.nastech.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.nastechresearch.nastech.AppScope
import io.github.nastechresearch.nastech.data.datastore.Settings
import io.github.nastechresearch.nastech.data.datastore.SettingsStore
import io.github.nastechresearch.nastech.data.ai.mcp.McpManager

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager,
    private val appScope: AppScope,
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(settings: Settings) {
        // Settings edits must survive leaving the settings screen immediately. Using the
        // application scope prevents a ViewModel cancellation from dropping the DataStore edit
        // while the user exits or navigates away after typing.
        appScope.launch {
            settingsStore.update(settings)
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        appScope.launch {
            settingsStore.update(transform)
        }
    }
}
