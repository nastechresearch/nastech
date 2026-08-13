package io.github.nastechresearch.nastech.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.data.datastore.BlackSilenceColorFamily
import io.github.nastechresearch.nastech.data.datastore.GlassAppearance
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.data.datastore.GlassSurfaceAppearance
import io.github.nastechresearch.nastech.ui.components.nav.BackButton
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingGlassAppearancePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val update: (GlassAppearance) -> Unit = { profile ->
        vm.updateSettings(settings.copy(glassAppearance = profile))
    }
    var selectedSurfaceGroup by remember { mutableStateOf<GlassSurfaceGroup?>(null) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Glass Appearance") },
                subtitle = { Text("Adjust Nastech materials globally or surface by surface") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GlassPreview(settings.glassAppearance)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CustomColors.cardColors) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Restore readable Nastech colors", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Reset tint, contrast, and text colors to the balanced Nastech dark appearance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { update(GlassAppearance()) }) { Text("Restore") }
                    }
                }
            }
            item {
                GlobalGlassControls(
                    profile = settings.glassAppearance,
                    onUpdate = update,
                )
            }
            item {
                SidebarControls(
                    widthDp = settings.displaySetting.drawerWidthDp,
                    onWidthChange = { width ->
                        vm.updateSettings(
                            settings.copy(
                                displaySetting = settings.displaySetting.copy(
                                    drawerWidthDp = width.coerceIn(280, 420),
                                ),
                            ),
                        )
                    },
                )
            }
            item {
                Text(
                    text = "Surface adjustments",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Open a focused category to change individual surfaces. Every surface uses the global profile until you switch off Use global.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(GlassSurfaceGroup.entries.size, key = { GlassSurfaceGroup.entries[it].name }) { index ->
                val group = GlassSurfaceGroup.entries[index]
                SurfaceGroupCard(
                    group = group,
                    profile = settings.glassAppearance,
                    onClick = { selectedSurfaceGroup = group },
                )
            }
        }
    }

    selectedSurfaceGroup?.let { group ->
        ModalBottomSheet(onDismissRequest = { selectedSurfaceGroup = null }) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(group.title, style = MaterialTheme.typography.titleLarge)
                    Text(group.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(group.surfaces.size, key = { group.surfaces[it].name }) { index ->
                    GlassSurfaceEditor(
                        surface = group.surfaces[index],
                        profile = settings.glassAppearance,
                        onUpdate = update,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassPreview(profile: GlassAppearance) {
    val tint = Color(profile.tintArgb)
    val container = if (profile.pureBlack) Color.Black else tint
    val primaryText = profile.primaryTextArgb?.let(::Color) ?: MaterialTheme.colorScheme.onSurface
    val secondaryText = profile.secondaryTextArgb?.let(::Color) ?: MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container.copy(alpha = profile.transparency.coerceIn(0.08f, 0.98f))),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = profile.borderOpacity.coerceIn(0f, 1f)),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Live preview", style = MaterialTheme.typography.titleMedium, color = primaryText)
            Text("Nastech surfaces update as you move the controls.", color = secondaryText)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.White.copy(alpha = profile.highlightOpacity.coerceIn(0f, 1f))),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Chat, cards, settings, and activity rows",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = primaryText,
                )
            }
        }
    }
}

@Composable
private fun GlobalGlassControls(profile: GlassAppearance, onUpdate: (GlassAppearance) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CustomColors.cardColors) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingSwitch("Enable Black Silence materials", profile.enabled) { onUpdate(profile.copy(enabled = it)) }
            BlackSilenceFamilyPicker(profile = profile, onUpdate = onUpdate)
            SettingSwitch("Pure black base", profile.pureBlack) { onUpdate(profile.copy(pureBlack = it)) }
            SettingSwitch("Blur where available", profile.blurEnabled) { onUpdate(profile.copy(blurEnabled = it)) }
            SettingSwitch("Ambient drift", profile.motionEnabled) { onUpdate(profile.copy(motionEnabled = it)) }
            SettingSwitch("Quiet / reduced motion", profile.reducedMotion) { onUpdate(profile.copy(reducedMotion = it)) }
            SettingSwitch("Sound-reactive glass", profile.soundReactive) { onUpdate(profile.copy(soundReactive = it)) }
            VisualColorPicker(
                label = "Glass tint",
                value = profile.tintArgb,
                supportingText = "Choose a visible material color. The selected swatch updates cards, chat, and navigation immediately.",
                presets = GlassTintPresets,
                onValueChange = { onUpdate(profile.copy(tintArgb = it)) },
            )
            GlassSlider("Transparency", profile.transparency) { onUpdate(profile.copy(transparency = it)) }
            GlassSlider("Blur strength", profile.blurIntensity) { onUpdate(profile.copy(blurIntensity = it)) }
            GlassSlider("Border opacity", profile.borderOpacity) { onUpdate(profile.copy(borderOpacity = it)) }
            GlassSlider("Highlight glow", profile.highlightOpacity) { onUpdate(profile.copy(highlightOpacity = it)) }
            GlassSlider("Color saturation", profile.saturation) { onUpdate(profile.copy(saturation = it)) }
            GlassSlider("Background brightness", profile.backgroundBrightness) { onUpdate(profile.copy(backgroundBrightness = it)) }
            HorizontalDivider()
            Text("Typography and colors", style = MaterialTheme.typography.titleSmall)
            Text(
                "These controls update foreground text, supporting copy, and interactive accents across Nastech.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OptionalHexColorEditor(
                label = "Primary text",
                value = profile.primaryTextArgb,
                fallback = MaterialTheme.colorScheme.onSurface.value.toLong(),
                supportingText = "Headings, body text, and icons",
                onValueChange = { onUpdate(profile.copy(primaryTextArgb = it)) },
                onReset = { onUpdate(profile.copy(primaryTextArgb = null)) },
            )
            OptionalHexColorEditor(
                label = "Secondary text",
                value = profile.secondaryTextArgb,
                fallback = MaterialTheme.colorScheme.onSurfaceVariant.value.toLong(),
                supportingText = "Descriptions, labels, and outline details",
                onValueChange = { onUpdate(profile.copy(secondaryTextArgb = it)) },
                onReset = { onUpdate(profile.copy(secondaryTextArgb = null)) },
            )
            OptionalHexColorEditor(
                label = "Accent color",
                value = profile.accentArgb,
                fallback = MaterialTheme.colorScheme.primary.value.toLong(),
                supportingText = "Buttons, selected states, and emphasis",
                onValueChange = { onUpdate(profile.copy(accentArgb = it)) },
                onReset = { onUpdate(profile.copy(accentArgb = null)) },
            )
            TextScaleSlider(profile.textScale) { onUpdate(profile.copy(textScale = it)) }
        }
    }
}

@Composable
private fun BlackSilenceFamilyPicker(
    profile: GlassAppearance,
    onUpdate: (GlassAppearance) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Black Silence colour family", style = MaterialTheme.typography.titleSmall)
        Text(
            "Every surface keeps the same quiet layout; the chosen family changes only the ambient bloom, active edge, and accent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(BlackSilenceColorFamily.entries, key = { it.name }) { family ->
                val selected = profile.colorFamily == family
                Surface(
                    onClick = {
                        onUpdate(
                            profile.copy(
                                colorFamily = family,
                                tintArgb = family.surfaceTintArgb,
                                accentArgb = family.accentArgb,
                            ),
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = Color(family.surfaceTintArgb),
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) Color(family.accentArgb) else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(Color(family.bloomPrimaryArgb), Color(family.bloomSecondaryArgb)),
                                    ),
                                ),
                        )
                        Text(
                            family.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = readableOn(Color(family.surfaceTintArgb)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarControls(widthDp: Int, onWidthChange: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CustomColors.cardColors) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Chat sidebar", style = MaterialTheme.typography.titleSmall)
            Text(
                "Set a comfortable drawer width. Its tint, transparency, blur, border, and highlight can be adjusted separately in Workspace surfaces.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Width: ${widthDp.coerceIn(280, 420)} dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = widthDp.coerceIn(280, 420).toFloat(),
                onValueChange = { onWidthChange(it.roundToInt()) },
                valueRange = 280f..420f,
            )
        }
    }
}

private enum class GlassSurfaceGroup(
    val title: String,
    val description: String,
    val surfaces: List<GlassSurface>,
) {
    WORKSPACE("Workspace", "Background, navigation, cards, settings, and sidebar surfaces.", listOf(GlassSurface.APP_BACKGROUND, GlassSurface.TOP_BAR, GlassSurface.BOTTOM_BAR, GlassSurface.SIDEBAR, GlassSurface.CARD, GlassSurface.LIST_ITEM, GlassSurface.SETTINGS)),
    CHAT("Chat", "Composer and message materials for a focused conversation view.", listOf(GlassSurface.CHAT_INPUT, GlassSurface.USER_BUBBLE, GlassSurface.ASSISTANT_BUBBLE, GlassSurface.ACTIVITY)),
    OVERLAYS("Overlays", "Dialogs and bottom sheets that sit above the workspace.", listOf(GlassSurface.DIALOG, GlassSurface.BOTTOM_SHEET)),
    CONTROLS("Controls", "Buttons and interactive material emphasis.", listOf(GlassSurface.BUTTON)),
}

@Composable
private fun SurfaceGroupCard(group: GlassSurfaceGroup, profile: GlassAppearance, onClick: () -> Unit) {
    val adjustedCount = group.surfaces.count { surface -> profile.surfaceOverrides[surface]?.inheritGlobal == false }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColors,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(profile.tintArgb)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleMedium)
                Text(group.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (adjustedCount == 0) "Using global appearance" else "$adjustedCount customized surface${if (adjustedCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GlassSurfaceEditor(surface: GlassSurface, profile: GlassAppearance, onUpdate: (GlassAppearance) -> Unit) {
    val override = profile.surfaceOverrides[surface] ?: GlassSurfaceAppearance()
    val effectiveTint = override.tintArgb ?: profile.tintArgb
    val effectiveTransparency = override.transparency ?: profile.transparency
    val effectiveBlur = override.blurIntensity ?: profile.blurIntensity
    val effectiveBorder = override.borderOpacity ?: profile.borderOpacity
    val effectiveHighlight = override.highlightOpacity ?: profile.highlightOpacity

    fun updateOverride(value: GlassSurfaceAppearance) {
        onUpdate(profile.copy(surfaceOverrides = profile.surfaceOverrides + (surface to value)))
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CustomColors.cardColors) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(surface.displayName(), style = MaterialTheme.typography.titleSmall)
            SettingSwitch("Use global", override.inheritGlobal) {
                updateOverride(override.copy(inheritGlobal = it))
            }
            if (!override.inheritGlobal) {
                VisualColorPicker(
                    label = "${surface.displayName()} tint",
                    value = effectiveTint,
                    supportingText = "Choose a color for this surface only.",
                    presets = GlassTintPresets,
                    onValueChange = { updateOverride(override.copy(tintArgb = it)) },
                )
                GlassSlider("Transparency", effectiveTransparency) {
                    updateOverride(override.copy(transparency = it))
                }
                GlassSlider("Blur strength", effectiveBlur) {
                    updateOverride(override.copy(blurIntensity = it, blurEnabled = it > 0f))
                }
                GlassSlider("Border opacity", effectiveBorder) {
                    updateOverride(override.copy(borderOpacity = it))
                }
                GlassSlider("Highlight glow", effectiveHighlight) {
                    updateOverride(override.copy(highlightOpacity = it))
                }
                OutlinedButton(
                    onClick = { onUpdate(profile.copy(surfaceOverrides = profile.surfaceOverrides - surface)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset ${surface.displayName()} to global") }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GlassSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label · ${(value.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange, valueRange = 0f..1f)
    }
}

@Composable
private fun OptionalHexColorEditor(
    label: String,
    value: Long?,
    fallback: Long,
    supportingText: String,
    onValueChange: (Long) -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VisualColorPicker(
            label = label,
            value = value ?: fallback,
            supportingText = supportingText,
            presets = ForegroundColorPresets,
            onValueChange = onValueChange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (value != null) {
                OutlinedButton(onClick = onReset) { Text("Use theme") }
            }
        }
    }
}

@Composable
private fun TextScaleSlider(value: Float, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Text size · ${"%.0f".format(value.coerceIn(0.85f, 1.30f) * 100)}%",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = value.coerceIn(0.85f, 1.30f),
            onValueChange = onValueChange,
            valueRange = 0.85f..1.30f,
        )
        Text(
            text = "Compact to accessibility-friendly, applied throughout the app.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VisualColorPicker(
    label: String,
    value: Long,
    supportingText: String,
    presets: List<AppearanceColorPreset>,
    onValueChange: (Long) -> Unit,
) {
    var showCustomField by remember(label) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(value)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(supportingText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(presets, key = { it.name }) { preset ->
                val selected = (value and 0xFFFFFFL) == (preset.argb and 0xFFFFFFL)
                Column(
                    modifier = Modifier
                        .clickable { onValueChange(preset.argb) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = Color(preset.argb),
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        ),
                    ) {
                        if (selected) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✓", color = readableOn(Color(preset.argb)), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedButton(onClick = { showCustomField = !showCustomField }) {
            Text(if (showCustomField) "Hide custom color" else "Use a custom color")
        }
        if (showCustomField) {
            CustomHexColorField(value = value, onValueChange = onValueChange)
        }
    }
}

@Composable
private fun CustomHexColorField(value: Long, onValueChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toHexColor()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.parseHexColor()?.let(onValueChange)
        },
        label = { Text("Custom color") },
        placeholder = { Text("#233044") },
        supportingText = { Text("Enter any #RRGGBB color") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

private data class AppearanceColorPreset(val name: String, val argb: Long)

private val GlassTintPresets = listOf(
    AppearanceColorPreset("Obsidian", 0xFF11101DL),
    AppearanceColorPreset("Sky", 0xFF0B1728L),
    AppearanceColorPreset("Emerald", 0xFF0B1B19L),
    AppearanceColorPreset("Violet", 0xFF181025L),
    AppearanceColorPreset("Sunset", 0xFF211017L),
    AppearanceColorPreset("Frost", 0xFFE8F4FFL),
)

private val ForegroundColorPresets = listOf(
    AppearanceColorPreset("Paper", 0xFFF7F8FCL),
    AppearanceColorPreset("Soft", 0xFFC7D2F0L),
    AppearanceColorPreset("Sky", 0xFFBFE1FFL),
    AppearanceColorPreset("Mint", 0xFFA7F3D0L),
    AppearanceColorPreset("Gold", 0xFFFDE68AL),
    AppearanceColorPreset("Rose", 0xFFFFC2D1L),
)

private fun readableOn(color: Color): Color {
    val luminance = (0.299f * color.red) + (0.587f * color.green) + (0.114f * color.blue)
    return if (luminance > 0.55f) Color(0xFF10131AL) else Color.White
}

private fun Long.toHexColor(): String = "#%06X".format(this and 0xFFFFFF)

private fun String.parseHexColor(): Long? {
    val raw = trim().removePrefix("#")
    if (raw.length != 6 || raw.any { it !in "0123456789abcdefABCDEF" }) return null
    return raw.toLongOrNull(16)?.or(0xFF000000L)
}

private fun GlassSurface.displayName(): String = when (this) {
    GlassSurface.APP_BACKGROUND -> "App background"
    GlassSurface.TOP_BAR -> "Top bar"
    GlassSurface.BOTTOM_BAR -> "Bottom navigation"
    GlassSurface.CARD -> "Cards"
    GlassSurface.LIST_ITEM -> "Lists"
    GlassSurface.CHAT_INPUT -> "Chat input"
    GlassSurface.USER_BUBBLE -> "Your messages"
    GlassSurface.ASSISTANT_BUBBLE -> "Assistant messages"
    GlassSurface.DIALOG -> "Dialogs"
    GlassSurface.BOTTOM_SHEET -> "Bottom sheets"
    GlassSurface.BUTTON -> "Buttons"
    GlassSurface.SETTINGS -> "Settings"
    GlassSurface.SIDEBAR -> "Chat sidebar"
    GlassSurface.ACTIVITY -> "Reasoning and tools"
}
