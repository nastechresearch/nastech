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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.ui.theme.LocalGlassAppearance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The reader request remains local to the current app session and never exports spoken text. */
data class ScreenReaderRequest(
    val title: String,
    val text: String,
    val focus: Boolean = false,
)

@Stable
class ScreenReaderState internal constructor(
    private val tts: CustomTtsState,
) {
    private val _activeRequest = MutableStateFlow<ScreenReaderRequest?>(null)
    val activeRequest: StateFlow<ScreenReaderRequest?> = _activeRequest.asStateFlow()

    fun start(title: String, text: String, focus: Boolean = false) {
        if (text.isBlank()) return
        _activeRequest.value = ScreenReaderRequest(title = title.ifBlank { "Nastech reader" }, text = text, focus = focus)
        tts.speak(text)
    }

    fun stop() {
        tts.stop()
        _activeRequest.value = null
    }

    fun pauseOrResume() {
        if (tts.isSpeaking.value) tts.pause() else tts.resume()
    }

    fun skip() = tts.skipNext()

    fun toggleFocus() {
        _activeRequest.value = _activeRequest.value?.let { it.copy(focus = !it.focus) }
    }
}

@Composable
fun rememberScreenReaderState(tts: CustomTtsState): ScreenReaderState = remember(tts) { ScreenReaderState(tts) }

/**
 * A quiet reader overlay. In normal mode it docks above the bottom edge and leaves the current
 * chat readable. Focus mode expands to a black surface while the route content is blurred by the
 * root composition. The text reveal is intentionally phrase-oriented; TTS providers do not expose
 * portable word timestamps, so the visual does not claim frame-perfect spoken-word timing.
 */
@Composable
fun ScreenReaderOverlay(state: ScreenReaderState, modifier: Modifier = Modifier) {
    val request by state.activeRequest.collectAsState()
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsState()
    val currentChunk by tts.currentChunk.collectAsState()
    val totalChunks by tts.totalChunks.collectAsState()
    val glass = LocalGlassAppearance.current
    val active = request ?: return
    val words = remember(active.text) { active.text.split(Regex("\\s+")).filter(String::isNotBlank) }
    var visibleWords by remember(active.text) { mutableIntStateOf(minOf(18, words.size)) }

    LaunchedEffect(active.text, isSpeaking, currentChunk) {
        if (isSpeaking) {
            while (visibleWords < words.size && tts.isSpeaking.value) {
                delay(78)
                visibleWords = minOf(words.size, visibleWords + 1)
            }
        }
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
                colors = CustomColors.cardColors,
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
                                text = if (isSpeaking) "Reading aloud" else "Reader paused",
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = active.title,
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BlackSilenceWaveform(
                            active = isSpeaking && glass.soundReactive && !glass.reducedMotion,
                            modifier = Modifier.size(width = 94.dp, height = 38.dp),
                        )
                    }
                    Text(
                        text = words.take(visibleWords).joinToString(" "),
                        style = if (active.focus) androidx.compose.material3.MaterialTheme.typography.titleLarge else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        maxLines = if (active.focus) 7 else 3,
                        overflow = TextOverflow.Ellipsis,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (totalChunks > 1) "Section ${currentChunk.coerceAtLeast(0) + 1} of $totalChunks" else "Nastech reads with your selected voice provider",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
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
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
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
