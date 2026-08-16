package io.github.nastechresearch.nastech.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

enum class NastechThemePreset(
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val background: Color,
    val container: Color
) {
    OBSERVATORY(
        primary = Color(0xFFA7F5D1),
        secondary = Color(0xFF8CC4FF),
        surface = Color(0xFF07100F),
        background = Color(0xFF040909),
        container = Color(0xFF0F1D1B)
    ),
    SPACE(
        primary = Color(0xFFB56CFF),
        secondary = Color(0xFF35D9FF),
        surface = Color(0xFF15121D),
        background = Color(0xFF090014),
        container = Color(0xFF221A33)
    ),
    WARM_CULINARY(
        primary = Color(0xFFFF9F6C),
        secondary = Color(0xFFFFD16C),
        surface = Color(0xFF1D1712),
        background = Color(0xFF100B07),
        container = Color(0xFF30241A)
    ),
    TERMINAL(
        primary = Color(0xFF83D9B7),
        secondary = Color(0xFF76B8FF),
        surface = Color(0xFF0E1412),
        background = Color(0xFF050807),
        container = Color(0xFF182421)
    )
}

object NastechAdaptiveThemeSelector {
    fun detectPreset(text: String): NastechThemePreset {
        val lower = text.lowercase()
        return when {
            lower.contains("space") || lower.contains("black hole") || lower.contains("galaxy") || lower.contains("star") -> NastechThemePreset.SPACE
            lower.contains("cook") || lower.contains("recipe") || lower.contains("food") || lower.contains("pasta") -> NastechThemePreset.WARM_CULINARY
            lower.contains("code") || lower.contains("termux") || lower.contains("terminal") || lower.contains("script") || lower.contains("command") -> NastechThemePreset.TERMINAL
            else -> NastechThemePreset.OBSERVATORY
        }
    }
}

@Composable
fun rememberAnimatedColorScheme(preset: NastechThemePreset): ColorScheme {
    val primary by animateColorAsState(preset.primary, spring(stiffness = Spring.StiffnessLow))
    val secondary by animateColorAsState(preset.secondary, spring(stiffness = Spring.StiffnessLow))
    val surface by animateColorAsState(preset.surface, tween(400))
    val background by animateColorAsState(preset.background, tween(400))
    val surfaceContainer by animateColorAsState(preset.container, tween(400))

    return darkColorScheme(
        primary = primary,
        secondary = secondary,
        surface = surface,
        background = background,
        surfaceContainer = surfaceContainer,
        onPrimary = Color(0xFF040909),
        onSurface = Color(0xFFEFFFF7)
    )
}
