package io.github.nastechresearch.nastech.ui.pages.setting

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.Screen
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
                GlobalGlassControls(
                    profile = settings.glassAppearance,
                    onUpdate = update,
                )
            }
            item {
                Text(
                    text = "Manual surface overrides",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Each surface inherits the global profile until you turn off Use global.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(GlassSurface.entries.size, key = { GlassSurface.entries[it].name }) { index ->
                val surface = GlassSurface.entries[index]
                GlassSurfaceEditor(
                    surface = surface,
                    profile = settings.glassAppearance,
                    onUpdate = update,
                )
            }
            item {
                OutlinedButton(
                    onClick = { update(GlassAppearance()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Restore default glass appearance")
                }
            }
        }
    }
}

@Composable
private fun GlassPreview(profile: GlassAppearance) {
    val tint = Color(profile.tintArgb)
    val container = if (profile.pureBlack) Color.Black else tint
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
            Text("Live preview", style = MaterialTheme.typography.titleMedium)
            Text("Nastech surfaces update as you move the controls.")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.White.copy(alpha = profile.highlightOpacity.coerceIn(0f, 1f))),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Chat, cards, settings, and activity rows", modifier = Modifier.padding(horizontal = 12.dp))
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
            SettingSwitch("Enable glass appearance", profile.enabled) { onUpdate(profile.copy(enabled = it)) }
            SettingSwitch("Pure Black Glass", profile.pureBlack) { onUpdate(profile.copy(pureBlack = it)) }
            SettingSwitch("Blur where available", profile.blurEnabled) { onUpdate(profile.copy(blurEnabled = it)) }
            SettingSwitch("Animated background light", profile.motionEnabled) { onUpdate(profile.copy(motionEnabled = it)) }
            HexTintEditor(
                label = "Global tint",
                value = profile.tintArgb,
                onValueChange = { onUpdate(profile.copy(tintArgb = it)) },
            )
            GlassSlider("Transparency", profile.transparency) { onUpdate(profile.copy(transparency = it)) }
            GlassSlider("Blur strength", profile.blurIntensity) { onUpdate(profile.copy(blurIntensity = it)) }
            GlassSlider("Border opacity", profile.borderOpacity) { onUpdate(profile.copy(borderOpacity = it)) }
            GlassSlider("Highlight glow", profile.highlightOpacity) { onUpdate(profile.copy(highlightOpacity = it)) }
            GlassSlider("Color saturation", profile.saturation) { onUpdate(profile.copy(saturation = it)) }
            GlassSlider("Background brightness", profile.backgroundBrightness) { onUpdate(profile.copy(backgroundBrightness = it)) }
            HorizontalDivider()
            Text("Quick presets", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onUpdate(GlassAppearance(pureBlack = true, tintArgb = 0xFF000000L, transparency = 0.74f, blurEnabled = true, blurIntensity = 0.62f)) }, modifier = Modifier.weight(1f)) { Text("Black") }
                Button(onClick = { onUpdate(GlassAppearance(tintArgb = 0xFFE8F4FFL, transparency = 0.48f, blurEnabled = true, blurIntensity = 0.72f)) }, modifier = Modifier.weight(1f)) { Text("Frost") }
                Button(onClick = { onUpdate(GlassAppearance(tintArgb = 0xFF142C58L, transparency = 0.70f, blurEnabled = true, blurIntensity = 0.60f)) }, modifier = Modifier.weight(1f)) { Text("Midnight") }
            }
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
                HexTintEditor(
                    label = "Custom tint",
                    value = effectiveTint,
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
private fun HexTintEditor(label: String, value: Long, onValueChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toHexColor()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color(value)),
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                it.parseHexColor()?.let(onValueChange)
            },
            label = { Text(label) },
            placeholder = { Text("#233044") },
            supportingText = { Text("Any #RRGGBB glass tint") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
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
    GlassSurface.ACTIVITY -> "Reasoning and tools"
}
