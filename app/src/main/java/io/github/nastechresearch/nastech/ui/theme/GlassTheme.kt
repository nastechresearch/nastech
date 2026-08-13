package io.github.nastechresearch.nastech.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
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
