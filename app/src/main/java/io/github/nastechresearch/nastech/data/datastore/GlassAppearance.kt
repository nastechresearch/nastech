package io.github.nastechresearch.nastech.data.datastore

import kotlinx.serialization.Serializable

/**
 * User-controlled material treatment for Nastech surfaces. Colors are stored as ARGB values so
 * the preference model stays independent from Compose rendering classes.
 */
@Serializable
data class GlassAppearance(
    val enabled: Boolean = true,
    /** Black Silence is the single reusable surface system used across Nastech. */
    val colorFamily: BlackSilenceColorFamily = BlackSilenceColorFamily.OBSIDIAN_NEON,
    val pureBlack: Boolean = true,
    val tintArgb: Long = 0xFF071A2D,
    val transparency: Float = 0.86f,
    val blurEnabled: Boolean = true,
    val blurIntensity: Float = 0.52f,
    val borderOpacity: Float = 0.34f,
    val highlightOpacity: Float = 0.20f,
    val saturation: Float = 1.03f,
    val backgroundBrightness: Float = 0.92f,
    val motionEnabled: Boolean = true,
    /** Keeps feedback while removing ambient drift and repeating animation. */
    val reducedMotion: Boolean = false,
    /** Lets compatible voice and media surfaces use a restrained active glow. */
    val soundReactive: Boolean = true,
    /** Contrast-safe AMOLED defaults; users can still override each foreground color. */
    val primaryTextArgb: Long? = 0xFFF5F9FF,
    val secondaryTextArgb: Long? = 0xFFB8D9FF,
    val accentArgb: Long? = 0xFF76B8FF,
    /** Multiplier applied to the entire Compose font scale, from compact to accessibility-friendly. */
    val textScale: Float = 1.0f,
    val surfaceOverrides: Map<GlassSurface, GlassSurfaceAppearance> = emptyMap(),
) {
    fun appearanceFor(surface: GlassSurface): GlassSurfaceAppearance =
        surfaceOverrides[surface]?.takeUnless { it.inheritGlobal } ?: GlassSurfaceAppearance()
}

@Serializable
enum class BlackSilenceColorFamily(
    val title: String,
    val surfaceTintArgb: Long,
    val accentArgb: Long,
    val bloomPrimaryArgb: Long,
    val bloomSecondaryArgb: Long,
) {
    OBSIDIAN_NEON("Obsidian Neon", 0xFF11101DL, 0xFF70E8FFL, 0xFF7C5CFFL, 0xFF00D4D4L),
    SKY_BLUE("Sky Blue", 0xFF0B1728L, 0xFF70E8FFL, 0xFF35C5FFL, 0xFF4B7DFFL),
    EMERALD("Emerald", 0xFF0B1B19L, 0xFF6EF0C0L, 0xFF31E8A5L, 0xFF008F6EL),
    VIOLET("Violet", 0xFF181025L, 0xFFB79AFFL, 0xFF8A5CFFL, 0xFF6C44D9L),
    SUNSET("Sunset", 0xFF211017L, 0xFFFFB878L, 0xFFFF8A65L, 0xFFFF4DB8L),
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
    /** Dedicated material for the conversation drawer/sidebar. */
    SIDEBAR,
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
