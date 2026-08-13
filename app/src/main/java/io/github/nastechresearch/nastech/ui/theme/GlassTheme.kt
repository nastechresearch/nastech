package io.github.nastechresearch.nastech.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.github.nastechresearch.nastech.data.datastore.GlassAppearance
import io.github.nastechresearch.nastech.data.datastore.GlassSurface

val LocalGlassAppearance = compositionLocalOf { GlassAppearance() }

data class ResolvedGlassSurface(
    val enabled: Boolean,
    val container: Color,
    val border: Color,
    val highlight: Color,
    val blurEnabled: Boolean,
    val blurIntensity: Float,
)

@Composable
@ReadOnlyComposable
fun glassSurface(surface: GlassSurface, fallback: Color): ResolvedGlassSurface {
    val appearance = LocalGlassAppearance.current
    if (!appearance.enabled) {
        return ResolvedGlassSurface(
            enabled = false,
            container = fallback,
            border = Color.Transparent,
            highlight = Color.Transparent,
            blurEnabled = false,
            blurIntensity = 0f,
        )
    }

    val override = appearance.surfaceOverrides[surface]?.takeUnless { it.inheritGlobal }
    val tint = override?.tintArgb?.let(::Color)
        ?: if (appearance.pureBlack) Color.Black else Color(appearance.tintArgb)
    val opacity = (override?.transparency ?: appearance.transparency).coerceIn(0.08f, 0.98f)
    val borderOpacity = (override?.borderOpacity ?: appearance.borderOpacity).coerceIn(0f, 1f)
    val highlightOpacity = (override?.highlightOpacity ?: appearance.highlightOpacity).coerceIn(0f, 1f)
    val blurEnabled = override?.blurEnabled ?: appearance.blurEnabled
    val blurIntensity = (override?.blurIntensity ?: appearance.blurIntensity).coerceIn(0f, 1f)

    return ResolvedGlassSurface(
        enabled = true,
        container = tint.copy(alpha = opacity),
        border = Color.White.copy(alpha = borderOpacity),
        highlight = Color.White.copy(alpha = highlightOpacity),
        blurEnabled = blurEnabled,
        blurIntensity = blurIntensity,
    )
}

@Composable
@ReadOnlyComposable
fun MaterialTheme.glassContainer(surface: GlassSurface, fallback: Color): Color =
    glassSurface(surface, fallback).container

/**
 * Resolves a readable foreground against the actual translucent glass surface, including the
 * active app background underneath it. This keeps text and icons visible for black, white, and
 * arbitrary user-selected tint overrides.
 */
@Composable
@ReadOnlyComposable
fun glassContentColor(surface: GlassSurface, fallback: Color): Color {
    val container = glassSurface(surface, fallback).container
    val visibleBackground = container.compositeOver(MaterialTheme.colorScheme.background)
    val preferred = MaterialTheme.colorScheme.onSurface
    return if (contrastRatio(preferred, visibleBackground) >= 4.5f) {
        preferred
    } else {
        val light = Color(0xFFF5F9FF)
        val dark = Color(0xFF08111D)
        if (contrastRatio(light, visibleBackground) >= contrastRatio(dark, visibleBackground)) light else dark
    }
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.relativeLuminance(), background.relativeLuminance())
    val darker = minOf(foreground.relativeLuminance(), background.relativeLuminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
