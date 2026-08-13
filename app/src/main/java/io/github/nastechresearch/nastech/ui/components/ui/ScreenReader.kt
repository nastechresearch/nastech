package io.github.nastechresearch.nastech.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nastechresearch.nastech.ui.context.LocalTTSState
import io.github.nastechresearch.nastech.ui.hooks.CustomTtsState
import io.github.nastechresearch.nastech.ui.theme.GlassSurface
import io.github.nastechresearch.nastech.ui.theme.LocalGlassAppearance
import io.github.nastechresearch.nastech.ui.theme.glassContentColor
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

/**
 * Reader state follows the existing TTS controller. Exact ranges are reserved for engines that
 * publish reliable character positions; all other providers present real queue phrases without
 * pretending to offer word-perfect timestamps.
 */
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
 * A quiet, Black Silence reader overlay. It advances only when the established controller changes
 * phrase, keeping spoken text, queue progress, and reader presentation on one timeline.
 */
@Composable
fun ScreenReaderOverlay(state: ScreenReaderState, modifier: Modifier = Modifier) {
    val request by state.activeRequest.collectAsState()
    val progress by state.progress.collectAsState()
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsState()
    val glass = LocalGlassAppearance.current
    val active = request ?: return
    var completedPhrases by remember(active.text) { mutableStateOf(emptyList<String>()) }
    var previousPhrase by remember(active.text) { mutableStateOf("") }
    val currentPhrase = progress.activeChunkText

    androidx.compose.runtime.LaunchedEffect(currentPhrase) {
        if (previousPhrase.isNotBlank() && previousPhrase != currentPhrase) {
            completedPhrases = (completedPhrases + previousPhrase).takeLast(3)
        }
        if (currentPhrase.isNotBlank()) previousPhrase = currentPhrase
    }

    val contentColor = glassContentColor(GlassSurface.CARD, MaterialTheme.colorScheme.onSurface)
    val statusText = when (progress.mode) {
        ReaderMode.PhraseEstimate -> "Reading phrase"
        ReaderMode.Waiting -> "Preparing next phrase"
        ReaderMode.Paused -> "Reader paused"
        is ReaderMode.ExactRange -> "Following spoken words"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (active.focus) Color.Black.copy(alpha = 0.82f) else Color.Transparent),
        contentAlignment = if (active.focus) Alignment.Center else Alignment.BottomCenter,
    ) {
        AnimatedVisibility(visible = true) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (active.focus) 22.dp else 14.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = glassSurface(GlassSurface.CARD, MaterialTheme.colorScheme.surfaceContainer).container,
                    contentColor = contentColor,
                ),
                border = BorderStroke(1.dp, contentColor.copy(alpha = 0.14f)),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = active.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BlackSilenceWaveform(
                            active = isSpeaking && glass.soundReactive && !glass.reducedMotion,
                            modifier = Modifier.size(width = 94.dp, height = 38.dp),
                        )
                    }
                    if (completedPhrases.isNotEmpty()) {
                        Text(
                            text = completedPhrases.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.5f),
                            maxLines = if (active.focus) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = currentPhrase.ifBlank {
                            if (progress.mode == ReaderMode.Waiting) "Waiting for the next complete phrase…"
                            else "Speech is paused."
                        },
                        style = if (active.focus) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                        maxLines = if (active.focus) 7 else 3,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor,
                    )
                    Text(
                        text = if (progress.totalChunks > 1) {
                            "Section ${progress.chunkIndex.coerceAtLeast(1)} of ${progress.totalChunks}"
                        } else {
                            "Nastech reads with your selected voice provider"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.68f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = state::pauseOrResume) { Text(if (isSpeaking) "Pause" else "Resume") }
                        OutlinedButton(onClick = state::skip) { Text("Next") }
                        OutlinedButton(onClick = state::toggleFocus) { Text(if (active.focus) "Dock" else "Focus") }
                        OutlinedButton(onClick = state::stop) { Text("Stop") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlackSilenceWaveform(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "readerWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "readerWavePhase",
    )
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val bars = 18
        val gap = size.width / (bars * 1.7f)
        val barWidth = gap * 0.72f
        repeat(bars) { index ->
            val harmonic = ((index % 5) + 1) / 5f
            val activity = if (active) 0.28f + harmonic * (0.32f + phase * 0.34f) else 0.16f
            val height = size.height * activity
            val x = index * gap * 1.7f + gap * 0.5f
            drawLine(
                color = accent.copy(alpha = if (active) 0.96f else 0.45f),
                start = androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2f),
                end = androidx.compose.ui.geometry.Offset(x, (size.height + height) / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
