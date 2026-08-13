package io.github.nastechresearch.nastech.ui.pages.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.nastechresearch.nastech.ui.theme.LocalDarkMode
import io.github.nastechresearch.nastech.ui.theme.LocalGlassAppearance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Black Silence is the shared atmospheric layer behind Nastech surfaces. It starts nearly black,
 * uses only two diffused colour blooms from the selected family, and moves slowly enough that chat
 * copy and controls remain the visual priority. Quiet mode freezes the blooms without removing
 * the spatial depth users use to distinguish translucent panels from the canvas.
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "blackSilenceAmbient")
    val phaseOne by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(52_000, easing = LinearEasing)),
        label = "blackSilencePrimaryBloom",
    )
    val phaseTwo by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(68_000, easing = LinearEasing)),
        label = "blackSilenceSecondaryBloom",
    )

    val dark = LocalDarkMode.current
    val glass = LocalGlassAppearance.current
    val quiet = glass.reducedMotion || !glass.motionEnabled
    val p1 = if (quiet) 0f else phaseOne
    val p2 = if (quiet) 0f else phaseTwo
    val family = glass.colorFamily
    val customTint = Color(glass.tintArgb)

    fun tune(color: Color): Color {
        val brightness = glass.backgroundBrightness.coerceIn(0.16f, 1f)
        val saturation = glass.saturation.coerceIn(0f, 1.25f)
        val dimmed = Color(
            red = color.red * brightness,
            green = color.green * brightness,
            blue = color.blue * brightness,
            alpha = color.alpha,
        )
        val luma = (dimmed.red + dimmed.green + dimmed.blue) / 3f
        return Color(
            red = (luma + (dimmed.red - luma) * saturation) * 0.92f + customTint.red * 0.08f,
            green = (luma + (dimmed.green - luma) * saturation) * 0.92f + customTint.green * 0.08f,
            blue = (luma + (dimmed.blue - luma) * saturation) * 0.92f + customTint.blue * 0.08f,
            alpha = dimmed.alpha,
        )
    }

    val base = if (dark) {
        arrayOf(
            0f to Color(0xFF05070B),
            0.52f to Color(0xFF070A10),
            1f to Color(0xFF030406),
        )
    } else {
        arrayOf(
            0f to Color(0xFFF4F8FC),
            0.52f to Color(0xFFEEF3F8),
            1f to Color(0xFFE8EEF4),
        )
    }
    val primaryBloom = tune(Color(family.bloomPrimaryArgb))
    val secondaryBloom = tune(Color(family.bloomSecondaryArgb))
    val primaryAlpha = if (dark) 0.18f else 0.12f
    val secondaryAlpha = if (dark) 0.12f else 0.09f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colorStops = base)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val radius = maxOf(width, height)
            drawBloom(
                center = Offset(
                    width * 0.86f + sin(p1) * width * 0.06f,
                    height * 0.10f + cos(p1 * 0.65f) * height * 0.04f,
                ),
                radius = radius * 0.67f,
                color = primaryBloom,
                alpha = primaryAlpha,
            )
            drawBloom(
                center = Offset(
                    width * 0.22f + cos(p2) * width * 0.05f,
                    height * 0.87f + sin(p2 * 0.72f) * height * 0.04f,
                ),
                radius = radius * 0.62f,
                color = secondaryBloom,
                alpha = secondaryAlpha,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.018f else 0.08f),
                        Color.Transparent,
                        Color.Black.copy(alpha = if (dark) 0.12f else 0.02f),
                    ),
                ),
            )
        }
        content()
    }
}

private fun DrawScope.drawBloom(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
