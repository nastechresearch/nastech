package io.github.nastechresearch.nastech.ui.context

import androidx.compose.runtime.compositionLocalOf
import io.github.nastechresearch.nastech.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

