package io.github.nastechresearch.nastech.ui.pages.setting

import io.github.nastechresearch.nastech.data.datastore.BlackSilenceColorFamily
import io.github.nastechresearch.nastech.data.datastore.GlassAppearance
import io.github.nastechresearch.nastech.data.datastore.Settings

/** One-tap visual profiles for the whole Black Silence experience, including chat surfaces. */
data class NastechVisualPreset(
    val id: String,
    val title: String,
    val description: String,
    val themeId: String,
    val glassAppearance: GlassAppearance,
) {
    fun applyTo(settings: Settings): Settings = settings.copy(
        dynamicColor = false,
        themeId = themeId,
        glassAppearance = glassAppearance,
    )

    fun matches(settings: Settings): Boolean = settings.themeId == themeId &&
        settings.glassAppearance.tintArgb == glassAppearance.tintArgb &&
        settings.glassAppearance.accentArgb == glassAppearance.accentArgb &&
        settings.glassAppearance.pureBlack == glassAppearance.pureBlack &&
        settings.glassAppearance.transparency == glassAppearance.transparency
}

private fun profile(
    family: BlackSilenceColorFamily,
    tint: Long = family.surfaceTintArgb,
    accent: Long = family.accentArgb,
    pureBlack: Boolean = true,
    transparency: Float = 0.86f,
    blur: Float = 0.52f,
    motion: Boolean = true,
    reducedMotion: Boolean = false,
    highlight: Float = 0.18f,
): GlassAppearance = GlassAppearance(
    enabled = true,
    colorFamily = family,
    pureBlack = pureBlack,
    tintArgb = tint,
    transparency = transparency,
    blurEnabled = true,
    blurIntensity = blur,
    borderOpacity = 0.30f,
    highlightOpacity = highlight,
    saturation = 1.03f,
    backgroundBrightness = if (pureBlack) 0.92f else 1f,
    motionEnabled = motion,
    reducedMotion = reducedMotion,
    soundReactive = true,
    primaryTextArgb = 0xFFF5F9FF,
    secondaryTextArgb = 0xFFB8D9FF,
    accentArgb = accent,
)

val NastechVisualPresets = listOf(
    NastechVisualPreset("amoled-black", "AMOLED Black", "Pure black · ice blue", "black", profile(BlackSilenceColorFamily.OBSIDIAN_NEON, tint = 0xFF000000, accent = 0xFF76B8FF, transparency = 0.94f, blur = 0.32f, motion = false, highlight = 0.10f)),
    NastechVisualPreset("animated-glass", "Animated Glass", "Obsidian · drifting cyan", "ocean", profile(BlackSilenceColorFamily.OBSIDIAN_NEON, tint = 0xFF0A1220, accent = 0xFF70E8FF, transparency = 0.76f, blur = 0.72f, highlight = 0.30f)),
    NastechVisualPreset("watery-glass", "Watery Glass", "Deep water · luminous blue", "ocean", profile(BlackSilenceColorFamily.SKY_BLUE, tint = 0xFF061B2D, accent = 0xFF54C7FF, transparency = 0.70f, blur = 0.80f, highlight = 0.34f)),
    NastechVisualPreset("aurora", "Aurora", "Violet · emerald light", "spring", profile(BlackSilenceColorFamily.EMERALD, tint = 0xFF10152B, accent = 0xFF7FE6D0, transparency = 0.74f, blur = 0.72f, highlight = 0.32f)),
    NastechVisualPreset("midnight-violet", "Midnight Violet", "Ink · electric violet", "sakura", profile(BlackSilenceColorFamily.VIOLET, tint = 0xFF120D23, accent = 0xFFB79AFF, transparency = 0.82f, blur = 0.62f, highlight = 0.26f)),
    NastechVisualPreset("emerald-night", "Emerald Night", "Forest glass · mint", "spring", profile(BlackSilenceColorFamily.EMERALD, tint = 0xFF071B19, accent = 0xFF6EF0C0, transparency = 0.84f, blur = 0.58f, highlight = 0.22f)),
    NastechVisualPreset("arctic", "Arctic", "Frosted blue · quiet", "minimal", profile(BlackSilenceColorFamily.SKY_BLUE, tint = 0xFF0A1828, accent = 0xFFB3E4FF, transparency = 0.90f, blur = 0.48f, motion = false, reducedMotion = true, highlight = 0.16f)),
    NastechVisualPreset("solar-flare", "Solar Flare", "Warm black · amber", "autumn", profile(BlackSilenceColorFamily.SUNSET, tint = 0xFF21110A, accent = 0xFFFFB878, transparency = 0.80f, blur = 0.62f, highlight = 0.32f)),
    NastechVisualPreset("rose-quartz", "Rose Quartz", "Smoked plum · rose", "sakura", profile(BlackSilenceColorFamily.SUNSET, tint = 0xFF24101E, accent = 0xFFFFA8CC, transparency = 0.80f, blur = 0.68f, highlight = 0.30f)),
    NastechVisualPreset("cobalt", "Cobalt", "Blue-black · cobalt", "ocean", profile(BlackSilenceColorFamily.SKY_BLUE, tint = 0xFF091329, accent = 0xFF6C9BFF, transparency = 0.88f, blur = 0.54f, highlight = 0.22f)),
    NastechVisualPreset("matrix", "Matrix", "Black glass · signal green", "spring", profile(BlackSilenceColorFamily.EMERALD, tint = 0xFF07140C, accent = 0xFF92F78D, transparency = 0.90f, blur = 0.44f, motion = false, highlight = 0.14f)),
    NastechVisualPreset("lavender-mist", "Lavender Mist", "Soft violet · diffuse", "sakura", profile(BlackSilenceColorFamily.VIOLET, tint = 0xFF1A1428, accent = 0xFFD6C6FF, pureBlack = false, transparency = 0.70f, blur = 0.82f, highlight = 0.38f)),
    NastechVisualPreset("deep-sea", "Deep Sea", "Navy glass · aqua", "ocean", profile(BlackSilenceColorFamily.SKY_BLUE, tint = 0xFF031E28, accent = 0xFF54E0D1, transparency = 0.86f, blur = 0.66f, highlight = 0.24f)),
    NastechVisualPreset("graphite", "Graphite", "Neutral black · silver", "minimal", profile(BlackSilenceColorFamily.OBSIDIAN_NEON, tint = 0xFF141518, accent = 0xFFD7DEE7, transparency = 0.94f, blur = 0.30f, motion = false, reducedMotion = true, highlight = 0.10f)),
    NastechVisualPreset("orchid", "Orchid", "Dark orchid · neon lilac", "sakura", profile(BlackSilenceColorFamily.VIOLET, tint = 0xFF23142A, accent = 0xFFE1A6FF, transparency = 0.78f, blur = 0.74f, highlight = 0.34f)),
    NastechVisualPreset("ember", "Ember", "Charcoal · ember orange", "autumn", profile(BlackSilenceColorFamily.SUNSET, tint = 0xFF22130E, accent = 0xFFFF8D5C, transparency = 0.88f, blur = 0.50f, motion = false, highlight = 0.20f)),
    NastechVisualPreset("tidal", "Tidal", "Blue-green · fluid glow", "ocean", profile(BlackSilenceColorFamily.SKY_BLUE, tint = 0xFF06232A, accent = 0xFF79F1DC, transparency = 0.72f, blur = 0.86f, highlight = 0.40f)),
    NastechVisualPreset("cosmic", "Cosmic", "Black space · starlight", "black", profile(BlackSilenceColorFamily.VIOLET, tint = 0xFF080A1B, accent = 0xFF9FB4FF, transparency = 0.84f, blur = 0.60f, highlight = 0.30f)),
    NastechVisualPreset("quiet-paper", "Quiet Paper", "Low motion · soft contrast", "claude", profile(BlackSilenceColorFamily.OBSIDIAN_NEON, tint = 0xFF17181A, accent = 0xFFB8D9FF, pureBlack = false, transparency = 0.94f, blur = 0.24f, motion = false, reducedMotion = true, highlight = 0.10f)),
    NastechVisualPreset("nastech-default", "Nastech Default", "Black Silence · blue and mint", "black", profile(BlackSilenceColorFamily.OBSIDIAN_NEON, tint = 0xFF11101D, accent = 0xFF76B8FF, transparency = 0.86f, blur = 0.52f, highlight = 0.18f)),
)
