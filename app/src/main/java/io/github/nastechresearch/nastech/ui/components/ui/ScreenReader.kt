package io.github.nastechresearch.nastech.ui.components.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.ui.context.LocalTTSState
import io.github.nastechresearch.nastech.ui.hooks.CustomTtsState
import io.github.nastechresearch.nastech.ui.theme.LocalGlassAppearance
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.rerere.tts.model.PlaybackStatus

/** The reader request remains local to the current app session and never exports spoken text. */
data class ScreenReaderRequest(
    val title: String,
    val text: String,
    val focus: Boolean = false,
)

sealed interface ReaderMode {
    data object PhraseEstimate : ReaderMode
    data object Waiting : ReaderMode
    data object Paused : ReaderMode
    data class ExactRange(val start: Int, val end: Int) : ReaderMode
}

data class ReaderProgress(
    val activeChunkText: String = "",
    val chunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val mode: ReaderMode = ReaderMode.Waiting,
)

@Stable
class ScreenReaderState internal constructor(
    private val tts: CustomTtsState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _activeRequest = MutableStateFlow<ScreenReaderRequest?>(null)
    val activeRequest: StateFlow<ScreenReaderRequest?> = _activeRequest.asStateFlow()

    private val _progress = MutableStateFlow(ReaderProgress())
    val progress: StateFlow<ReaderProgress> = _progress.asStateFlow()

    init {
        scope.launch {
            combine(
                tts.isSpeaking,
                tts.currentChunk,
                tts.totalChunks,
                tts.activeChunkText,
                tts.playbackState,
            ) { speaking, chunk, total, activeText, playback ->
                val mode = when {
                    playback.status == PlaybackStatus.Paused -> ReaderMode.Paused
                    activeText.isNotBlank() -> ReaderMode.PhraseEstimate
                    speaking -> ReaderMode.Waiting
                    else -> ReaderMode.Paused
                }
                ReaderProgress(
                    activeChunkText = activeText,
                    chunkIndex = chunk,
                    totalChunks = total,
                    mode = mode,
                )
            }.collect { current ->
                if (_activeRequest.value != null) _progress.value = current
            }
        }
    }

    fun start(title: String, text: String, focus: Boolean = false) {
        if (text.isBlank()) return
        _activeRequest.value = ScreenReaderRequest(
            title = title.ifBlank { "Nastech reader" },
            text = text,
            focus = focus,
        )
        _progress.value = ReaderProgress(mode = ReaderMode.Waiting)
        tts.speak(text)
    }

    fun stop() {
        tts.stop()
        _activeRequest.value = null
        _progress.value = ReaderProgress()
    }

    fun pauseOrResume() {
        if (tts.isSpeaking.value) tts.pause() else tts.resume()
    }

    fun skip() = tts.skipNext()

    fun toggleFocus() {
        _activeRequest.value = _activeRequest.value?.let { it.copy(focus = !it.focus) }
    }

    fun dispose() = scope.cancel()
}

@Composable
fun rememberScreenReaderState(tts: CustomTtsState): ScreenReaderState = remember(tts) { ScreenReaderState(tts) }

/**
 * A deliberately quiet speaking indicator. The former docked reader panel is removed; the drop
 * is visible only while the connected controller is actually playing speech. Tapping it stops
 * the current readout without exposing spoken content on screen.
 */
@Composable
fun ScreenReaderOverlay(state: ScreenReaderState, modifier: Modifier = Modifier) {
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsState()
    if (!isSpeaking) return

    val appearance = LocalGlassAppearance.current
    val dropSurface = glassSurface(GlassSurface.ACTIVITY, MaterialTheme.colorScheme.surfaceContainer)
    val transition = rememberInfiniteTransition(label = "nastechSpeakingDrop")
    val scale by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.13f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (appearance.reducedMotion) 1500 else 900,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "nastechSpeakingDropScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .size(50.dp)
                .scale(if (appearance.soundReactive && !appearance.reducedMotion) scale else 1f)
                .semantics { contentDescription = "Stop speaking" }
                .clickable(onClick = state::stop),
            shape = CircleShape,
            color = dropSurface.container,
            shadowElevation = 4.dp,
        ) {
            BreathingDrop(
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BreathingDrop(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val drop = Path().apply {
            moveTo(size.width / 2f, size.height * 0.04f)
            cubicTo(
                size.width * 0.80f,
                size.height * 0.38f,
                size.width * 0.87f,
                size.height * 0.62f,
                size.width / 2f,
                size.height * 0.95f,
            )
            cubicTo(
                size.width * 0.13f,
                size.height * 0.62f,
                size.width * 0.20f,
                size.height * 0.38f,
                size.width / 2f,
                size.height * 0.04f,
            )
            close()
        }
        drawPath(drop, color)
        drawCircle(
            color = Color.White.copy(alpha = 0.54f),
            radius = size.minDimension * 0.11f,
            center = Offset(size.width * 0.41f, size.height * 0.52f),
        )
    }
}
