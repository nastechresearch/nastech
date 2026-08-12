package io.github.nastechresearch.nastech.ui.context

import androidx.compose.runtime.compositionLocalOf
import io.github.nastechresearch.nastech.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
