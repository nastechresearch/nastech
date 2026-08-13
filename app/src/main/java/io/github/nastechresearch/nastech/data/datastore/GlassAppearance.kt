package io.github.nastechresearch.nastech.data.datastore

import kotlinx.serialization.Serializable

/**
 * User-controlled material treatment for Nastech surfaces. Colors are stored as ARGB values so
 * the preference model stays independent from Compose rendering classes.
 */
@Serializable
data class GlassAppearance(
    val enabled: Boolean = true,
    val pureBlack: Boolean = false,
    val tintArgb: Long = 0xFF233044L,
    val transparency: Float = 0.72f,
    val blurEnabled: Boolean = true,
    val blurIntensity: Float = 0.55f,
    val borderOpacity: Float = 0.28f,
    val highlightOpacity: Float = 0.16f,
    val saturation: Float = 1.0f,
    val backgroundBrightness: Float = 1.0f,
    val motionEnabled: Boolean = true,
    /** Optional global foreground overrides. Null retains the active Material color scheme. */
    val primaryTextArgb: Long? = null,
    val secondaryTextArgb: Long? = null,
    val accentArgb: Long? = null,
    /** Multiplier applied to the entire Compose font scale, from compact to accessibility-friendly. */
    val textScale: Float = 1.0f,
    val surfaceOverrides: Map<GlassSurface, GlassSurfaceAppearance> = emptyMap(),
) {
    fun appearanceFor(surface: GlassSurface): GlassSurfaceAppearance =
        surfaceOverrides[surface]?.takeUnless { it.inheritGlobal } ?: GlassSurfaceAppearance()
}

@Serializable
enum class GlassSurface {
    APP_BACKGROUND,
    TOP_BAR,
    BOTTOM_BAR,
    CARD,
    LIST_ITEM,
    CHAT_INPUT,
    USER_BUBBLE,
    ASSISTANT_BUBBLE,
    DIALOG,
    BOTTOM_SHEET,
    BUTTON,
    SETTINGS,
    ACTIVITY,
}

@Serializable
data class GlassSurfaceAppearance(
    val inheritGlobal: Boolean = true,
    val tintArgb: Long? = null,
    val transparency: Float? = null,
    val blurEnabled: Boolean? = null,
    val blurIntensity: Float? = null,
    val borderOpacity: Float? = null,
    val highlightOpacity: Float? = null,
)
