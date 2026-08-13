package io.github.nastechresearch.nastech.ui.pages.setting

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.data.telegram.TelegramBotConfig
import io.github.nastechresearch.nastech.data.telegram.TelegramBotPreferences
import io.github.nastechresearch.nastech.service.TelegramBotService
import io.github.nastechresearch.nastech.ui.components.nav.BackButton
import io.github.nastechresearch.nastech.ui.context.LocalToaster
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import org.koin.compose.koinInject

private enum class TelegramEditor { TOKEN, CHAT_ID, ALLOWED_IDS, PROXY, SERVICE }

@Composable
fun SettingTelegramPage() {
    val prefs: TelegramBotPreferences = koinInject()
    val config by prefs.flow.collectAsStateWithLifecycle(initialValue = TelegramBotConfig())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settingsGlass = glassSurface(GlassSurface.SETTINGS, MaterialTheme.colorScheme.surfaceContainerHigh)
    var editor by remember { mutableStateOf<TelegramEditor?>(null) }

    fun setRunning(running: Boolean) = scope.launch {
        prefs.update { it.copy(enabled = running) }
        if (running) TelegramBotService.start(context) else TelegramBotService.stop(context)
    }

    Scaffold(
        topBar = { LargeFlexibleTopAppBar(title = { Text("Telegram") }, navigationIcon = { BackButton() }, scrollBehavior = scrollBehavior, colors = CustomColors.topBarColors) },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        ) {
            item { TelegramStatusCard(config.enabled, TelegramBotService.isRunning, settingsGlass.container, settingsGlass.border) { setRunning(!config.enabled) } }
            items(listOf(
                TelegramTile("Bot Token", if (config.token.isBlank()) "Not configured" else "Configured", TelegramEditor.TOKEN),
                TelegramTile("Telegram ID", config.defaultChatId?.toString() ?: "Not configured", TelegramEditor.CHAT_ID),
                TelegramTile("Allowed IDs", if (config.whitelist.isEmpty()) "No access IDs" else "${config.whitelist.size} allowed", TelegramEditor.ALLOWED_IDS),
                TelegramTile("Proxy", if (config.proxyEnabled) "${config.proxyType} enabled" else "Disabled", TelegramEditor.PROXY),
                TelegramTile("Service", if (config.enabled) "Running on boot" else "Stopped", TelegramEditor.SERVICE),
            )) { tile -> TelegramGridTile(tile, settingsGlass.container, settingsGlass.border) { editor = tile.editor } }
        }
    }

    editor?.let { active ->
        TelegramEditorSheet(active, config, onDismiss = { editor = null }) { update -> scope.launch { prefs.update(update); editor = null } }
    }
}

private data class TelegramTile(val title: String, val summary: String, val editor: TelegramEditor)

@Composable private fun TelegramGridTile(tile: TelegramTile, container: androidx.compose.ui.graphics.Color, border: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = container), border = androidx.compose.foundation.BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(tile.title, style = MaterialTheme.typography.titleMedium); Text(tile.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun TelegramStatusCard(enabled: Boolean, running: Boolean, container: androidx.compose.ui.graphics.Color, border: androidx.compose.ui.graphics.Color, onToggle: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = container), border = androidx.compose.foundation.BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Telegram bot", style = MaterialTheme.typography.titleLarge); Text(if (enabled && running) "Running" else if (enabled) "Starting" else "Stopped", color = MaterialTheme.colorScheme.primary); Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Icon(if (enabled) HugeIcons.Stop else HugeIcons.Play, null); Text(if (enabled) " Stop" else " Start") } }
    }
}

@Composable private fun TelegramEditorSheet(editor: TelegramEditor, config: TelegramBotConfig, onDismiss: () -> Unit, onSave: (TelegramBotConfig.() -> TelegramBotConfig) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
        when (editor) {
            TelegramEditor.TOKEN -> TelegramTextEditor("Bot Token", config.token, true, "Paste the token from BotFather. Stop the bot before editing.", enabled = !config.enabled) { value -> onSave { copy(token = value.trim()) } }
            TelegramEditor.CHAT_ID -> TelegramTextEditor("Telegram ID", config.defaultChatId?.toString().orEmpty(), false, "Numeric default chat ID for bot replies.", KeyboardType.Number, enabled = !config.enabled) { value -> onSave { copy(defaultChatId = value.toLongOrNull()) } }
            TelegramEditor.ALLOWED_IDS -> TelegramTextEditor("Allowed IDs", config.whitelist.sorted().joinToString(","), false, "Comma-separated Telegram IDs that may use this bot.", KeyboardType.Number, enabled = !config.enabled) { value -> onSave { copy(whitelist = value.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()) } }
            TelegramEditor.PROXY -> TelegramProxyEditor(config, canEdit = !config.enabled, onSave = onSave)
            TelegramEditor.SERVICE -> TelegramTextEditor("Service", if (config.enabled) "The bot is enabled and will start on device boot." else "The bot is stopped.", false, "Use the Service tile to start or stop it.", enabled = false) { }
        }
    }
}

@Composable private fun TelegramTextEditor(title: String, initial: String, secret: Boolean, note: String, keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }; var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall); Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedTextField(value = value, onValueChange = { value = it }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = !title.contains("Allowed"), keyboardOptions = KeyboardOptions(keyboardType = keyboard), visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None, trailingIcon = if (secret) {{ IconButton(onClick = { visible = !visible }) { Icon(if (visible) HugeIcons.ViewOff else HugeIcons.View, null) } }} else null); if (enabled) Button(onClick = { onSave(value) }, modifier = Modifier.fillMaxWidth()) { Text("Save") } }
}

@Composable private fun TelegramProxyEditor(config: TelegramBotConfig, canEdit: Boolean, onSave: (TelegramBotConfig.() -> TelegramBotConfig) -> Unit) {
    var proxyEnabled by remember { mutableStateOf(config.proxyEnabled) }; var host by remember { mutableStateOf(config.proxyHost) }; var port by remember { mutableStateOf(config.proxyPort.toString()) }; var user by remember { mutableStateOf(config.proxyUsername) }; var password by remember { mutableStateOf(config.proxyPassword) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Proxy", style = MaterialTheme.typography.headlineSmall); Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable proxy", modifier = Modifier.weight(1f)); Switch(proxyEnabled, { proxyEnabled = it }, enabled = canEdit) }; OutlinedTextField(host, { host = it }, enabled = canEdit, label = { Text("Host") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(port, { port = it.filter(Char::isDigit) }, enabled = canEdit, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()); OutlinedTextField(user, { user = it }, enabled = canEdit, label = { Text("Username") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(password, { password = it }, enabled = canEdit, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); if (canEdit) Button(onClick = { onSave { copy(proxyEnabled = proxyEnabled, proxyHost = host.trim(), proxyPort = port.toIntOrNull() ?: 0, proxyUsername = user, proxyPassword = password) } }, modifier = Modifier.fillMaxWidth()) { Text("Save proxy") } }
}
