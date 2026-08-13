package io.github.nastechresearch.nastech.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.github.nastechresearch.nastech.R

val base = Typography()
val Typography = Typography()

/**
 * Applies the user-selected text scale to the Material typography system. Keeping this at the
 * theme boundary means titles, controls, chips, and settings rows remain visually coherent.
 */
fun nastechTypography(scale: Float): Typography {
    val multiplier = scale.coerceIn(0.85f, 1.30f)
    fun TextStyle.scaled() = copy(
        fontSize = if (fontSize.isSpecified) fontSize * multiplier else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * multiplier else lineHeight,
    )
    return Typography.copy(
        displayLarge = Typography.displayLarge.scaled(),
        displayMedium = Typography.displayMedium.scaled(),
        displaySmall = Typography.displaySmall.scaled(),
        headlineLarge = Typography.headlineLarge.scaled(),
        headlineMedium = Typography.headlineMedium.scaled(),
        headlineSmall = Typography.headlineSmall.scaled(),
        titleLarge = Typography.titleLarge.scaled(),
        titleMedium = Typography.titleMedium.scaled(),
        titleSmall = Typography.titleSmall.scaled(),
        bodyLarge = Typography.bodyLarge.scaled(),
        bodyMedium = Typography.bodyMedium.scaled(),
        bodySmall = Typography.bodySmall.scaled(),
        labelLarge = Typography.labelLarge.scaled(),
        labelMedium = Typography.labelMedium.scaled(),
        labelSmall = Typography.labelSmall.scaled(),
    )
}

// Set of Material typography styles to start with
//val Typography = Typography(
//    displayLargeEmphasized = base.displayLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Display.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    displayMediumEmphasized = base.displayMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Display.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    displaySmallEmphasized = base.displaySmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Display.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Headline.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    headlineMediumEmphasized = base.headlineMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Headline.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    headlineSmallEmphasized = base.headlineSmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Headline.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    titleLargeEmphasized = base.titleLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Title.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    titleMediumEmphasized = base.titleMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Title.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    titleSmallEmphasized = base.titleSmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Title.Emphasized.Small,
//        fontWeight = FontWeight.Bold
//    ),
//    bodyLargeEmphasized = base.bodyLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Body.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    bodyMediumEmphasized = base.bodyMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Body.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    bodySmallEmphasized = base.bodySmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Body.Emphasized.Small,
//        fontWeight = FontWeight.Bold
//    ),
//    labelLargeEmphasized = base.labelLargeEmphasized.copy(
//        fontFamily = GoogleSansFlex.Label.Emphasized.Large,
//        fontWeight = FontWeight.Bold
//    ),
//    labelMediumEmphasized = base.labelMediumEmphasized.copy(
//        fontFamily = GoogleSansFlex.Label.Emphasized.Medium,
//        fontWeight = FontWeight.Bold
//    ),
//    labelSmallEmphasized = base.labelSmallEmphasized.copy(
//        fontFamily = GoogleSansFlex.Label.Emphasized.Small,
//        fontWeight = FontWeight.Bold
//    ),
//)

@OptIn(ExperimentalTextApi::class)
val JetbrainsMono = FontFamily(
    Font(
        resId = R.font.jetbrains_mono,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight),
        )
    )
)
