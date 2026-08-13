package io.github.nastechresearch.nastech.ui.context

import androidx.compose.runtime.compositionLocalOf
import io.github.nastechresearch.nastech.ui.components.ui.ScreenReaderState

val LocalScreenReaderState = compositionLocalOf<ScreenReaderState> { error("Screen reader state is not provided") }
