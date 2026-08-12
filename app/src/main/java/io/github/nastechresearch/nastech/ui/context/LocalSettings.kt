package io.github.nastechresearch.nastech.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.nastechresearch.nastech.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
