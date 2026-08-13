package io.github.nastechresearch.nastech.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import io.github.nastechresearch.nastech.ui.hooks.rememberAmoledDarkMode
import io.github.nastechresearch.nastech.ui.hooks.rememberCurrentColorMode
import io.github.nastechresearch.nastech.ui.hooks.rememberUserSettingsState

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)
private val BLACK_SILENCE_SURFACE = Color(0xFF080C12)
private val BLACK_SILENCE_SURFACE_LOW = Color(0xFF0B1018)
private val BLACK_SILENCE_SURFACE_HIGH = Color(0xFF101722)
private val AMOLED_TEXT = Color(0xFFF5F9FF)
private val AMOLED_SECONDARY_TEXT = Color(0xFFB8D9FF)
private val AMOLED_LIGHT_BLUE = Color(0xFF76B8FF)
private val AMOLED_MINT = Color(0xFF83D9B7)

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun NastechTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val theme = findThemeById(settings.themeId, settings.customThemes)
                ?: findPresetTheme(settings.themeId)
            theme.getColorScheme(dark = darkTheme)
        }
    }
    val colorSchemeConverted = remember(darkTheme, amoledDarkMode, colorScheme) {
        if (darkTheme && amoledDarkMode) {
            colorScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = BLACK_SILENCE_SURFACE,
                surfaceVariant = BLACK_SILENCE_SURFACE_LOW,
                surfaceContainerLowest = Color(0xFF05070B),
                surfaceContainerLow = BLACK_SILENCE_SURFACE,
                surfaceContainer = BLACK_SILENCE_SURFACE_LOW,
                surfaceContainerHigh = BLACK_SILENCE_SURFACE_HIGH,
                surfaceContainerHighest = Color(0xFF162131),
                primary = AMOLED_LIGHT_BLUE,
                onPrimary = AMOLED_DARK_BACKGROUND,
                primaryContainer = Color(0xFF0B3157),
                onPrimaryContainer = AMOLED_TEXT,
                secondary = AMOLED_MINT,
                onSecondary = AMOLED_DARK_BACKGROUND,
                secondaryContainer = Color(0xFF0D302C),
                onSecondaryContainer = AMOLED_TEXT,
                tertiary = Color(0xFF91D8C4),
                onTertiary = AMOLED_DARK_BACKGROUND,
                tertiaryContainer = Color(0xFF12372F),
                onTertiaryContainer = AMOLED_TEXT,
                onBackground = AMOLED_TEXT,
                onSurface = AMOLED_TEXT,
                onSurfaceVariant = AMOLED_SECONDARY_TEXT,
                outline = AMOLED_SECONDARY_TEXT.copy(alpha = 0.72f),
            )
        } else {
            colorScheme
        }
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors
    val appearanceColorScheme = remember(colorSchemeConverted, settings.glassAppearance) {
        val appearance = settings.glassAppearance
        // The canvas may be pure black, while each panel uses the selected near-black family tint.
        val visibleSurface = Color(appearance.tintArgb)
        val primaryText = contrastSafeForeground(
            requested = appearance.primaryTextArgb?.let(::Color),
            background = visibleSurface,
            fallback = colorSchemeConverted.onSurface,
        )
        val secondaryText = contrastSafeForeground(
            requested = appearance.secondaryTextArgb?.let(::Color),
            background = visibleSurface,
            fallback = colorSchemeConverted.onSurfaceVariant,
            minimumRatio = 3.2f,
        )
        val accent = appearance.accentArgb?.let(::Color)
        colorSchemeConverted.copy(
            primary = accent ?: colorSchemeConverted.primary,
            secondary = accent?.copy(alpha = 0.86f) ?: colorSchemeConverted.secondary,
            tertiary = accent?.copy(alpha = 0.72f) ?: colorSchemeConverted.tertiary,
            onBackground = contrastSafeForeground(primaryText, colorSchemeConverted.background, colorSchemeConverted.onBackground),
            onSurface = primaryText,
            onSurfaceVariant = secondaryText,
            outline = secondaryText.copy(alpha = 0.72f),
        )
    }

    // 更新状态栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalGlassAppearance provides settings.glassAppearance,
        LocalOverscrollFactory provides null
    ) {
        MaterialExpressiveTheme(
            colorScheme = appearanceColorScheme,
            typography = nastechTypography(settings.glassAppearance.textScale),
            content = content,
            motionScheme = MotionScheme.expressive()
        )
    }
}

private fun contrastSafeForeground(
    requested: Color?,
    background: Color,
    fallback: Color,
    minimumRatio: Float = 4.5f,
): Color {
    val preferred = requested ?: fallback
    if (contrastRatio(preferred, background) >= minimumRatio) return preferred
    val light = Color(0xFFF5F7FF)
    val dark = Color(0xFF101318)
    return if (contrastRatio(light, background) >= contrastRatio(dark, background)) light else dark
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.relativeLuminance(), background.relativeLuminance())
    val darker = minOf(foreground.relativeLuminance(), background.relativeLuminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float = if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).let { it * it * it }
    return (0.2126f * channel(red)) + (0.7152f * channel(green)) + (0.0722f * channel(blue))
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
