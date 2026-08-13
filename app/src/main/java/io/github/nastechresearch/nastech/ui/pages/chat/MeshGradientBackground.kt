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
 * A soft, animated glass-inspired canvas for chat surfaces.
 *
 * A low-contrast base gradient provides the frosted foundation while four independently moving
 * radial lights add depth without introducing hard edges or distracting color shifts. The effect
 * is deliberately implemented without a blur modifier so it remains smooth on the full Android
 * API range supported by Nastech.
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "glassAurora")

    @Composable
    fun phase(durationMillis: Int, loops: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI * loops).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis * loops, easing = LinearEasing)),
        label = label,
    )

    // Independent cycles prevent visible repetition while keeping movement deliberately calm.
    val p1 by phase(5_500, loops = 20, "blue")
    val p2 by phase(7_000, loops = 1, "mint")
    val p3 by phase(8_500, loops = 10, "lavender")
    val p4 by phase(6_200, loops = 10, "rose")

    val dark = LocalDarkMode.current
    val glass = LocalGlassAppearance.current
    val motionP1 = if (glass.motionEnabled) p1 else 0f
    val motionP2 = if (glass.motionEnabled) p2 else 0f
    val motionP3 = if (glass.motionEnabled) p3 else 0f
    val motionP4 = if (glass.motionEnabled) p4 else 0f
    val tint = Color(glass.tintArgb)
    fun tune(color: Color): Color {
        val brightness = glass.backgroundBrightness.coerceIn(0f, 1f)
        val saturation = glass.saturation.coerceIn(0f, 1f)
        val brightnessAdjusted = Color(
            red = color.red * brightness,
            green = color.green * brightness,
            blue = color.blue * brightness,
            alpha = color.alpha,
        )
        val luminance = (brightnessAdjusted.red + brightnessAdjusted.green + brightnessAdjusted.blue) / 3f
        val saturated = Color(
            red = luminance + (brightnessAdjusted.red - luminance) * saturation,
            green = luminance + (brightnessAdjusted.green - luminance) * saturation,
            blue = luminance + (brightnessAdjusted.blue - luminance) * saturation,
            alpha = brightnessAdjusted.alpha,
        )
        return Color(
            red = saturated.red * 0.90f + tint.red * 0.10f,
            green = saturated.green * 0.90f + tint.green * 0.10f,
            blue = saturated.blue * 0.90f + tint.blue * 0.10f,
            alpha = saturated.alpha,
        )
    }
    val baseGradient = if (dark) {
        arrayOf(
            0.0f to tune(Color(0xFF17233A)),
            0.26f to tune(Color(0xFF101A2C)),
            0.54f to tune(Color(0xFF0B1220)),
            0.76f to tune(Color(0xFF080E19)),
            1.0f to tune(Color(0xFF070B14)),
        )
    } else {
        arrayOf(
            0.0f to tune(Color(0xFFE7F1FF)),
            0.24f to tune(Color(0xFFF0F6FF)),
            0.52f to tune(Color(0xFFF8FBFF)),
            0.76f to tune(Color(0xFFFCFDFF)),
            1.0f to tune(Color(0xFFFFFFFF)),
        )
    }

    // Translucent cool lights preserve text contrast and read as light diffused through glass.
    val blobBlue = tune(if (dark) Color(0xFF7BA9E8) else Color(0xFF88B7F2))
    val blobMint = tune(if (dark) Color(0xFF62C9BE) else Color(0xFF94DED3))
    val blobLavender = tune(if (dark) Color(0xFFA7B7F3) else Color(0xFFC5D2FF))
    val blobRose = tune(if (dark) Color(0xFFD798B8) else Color(0xFFF2C2D6))
    val alphaBlue = if (dark) 0.27f else 0.40f
    val alphaMint = if (dark) 0.20f else 0.30f
    val alphaLavender = if (dark) 0.20f else 0.28f
    val alphaRose = if (dark) 0.14f else 0.20f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colorStops = baseGradient)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val radius = maxOf(width, height)

            drawBlob(
                center = Offset(
                    width * 0.48f + sin(motionP1) * width * 0.35f,
                    height * 0.07f + cos(motionP1 * 1.15f) * height * 0.17f,
                ),
                radius = radius * 0.42f,
                color = blobBlue,
                centerAlpha = alphaBlue,
            )
            drawBlob(
                center = Offset(
                    width * 0.17f + sin(motionP2 + PI.toFloat() * 0.55f) * width * 0.28f,
                    height * 0.25f + cos(motionP2) * height * 0.18f,
                ),
                radius = radius * 0.33f,
                color = blobMint,
                centerAlpha = alphaMint,
            )
            drawBlob(
                center = Offset(
                    width * 0.84f + sin(motionP3 + PI.toFloat() * 0.9f) * width * -0.31f,
                    height * 0.13f + cos(motionP3 * 0.9f) * height * 0.17f,
                ),
                radius = radius * 0.35f,
                color = blobLavender,
                centerAlpha = alphaLavender,
            )
            drawBlob(
                center = Offset(
                    width * 0.60f + sin(motionP4 + PI.toFloat() * 1.25f) * width * 0.26f,
                    height * 0.36f + cos(motionP4 * 1.1f) * height * 0.15f,
                ),
                radius = radius * 0.29f,
                color = blobRose,
                centerAlpha = alphaRose,
            )

            // A translucent wash visually integrates the moving lights behind foreground cards.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.025f else 0.13f),
                        Color.Transparent,
                        Color.White.copy(alpha = if (dark) 0.01f else 0.05f),
                    ),
                ),
            )
        }

        content()
    }
}

private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    color: Color,
    centerAlpha: Float = 0.75f,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = centerAlpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
