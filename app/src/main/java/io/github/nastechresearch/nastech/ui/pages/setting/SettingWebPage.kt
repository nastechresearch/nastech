package io.github.nastechresearch.nastech.ui.pages.setting

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.data.datastore.SettingsStore
import io.github.nastechresearch.nastech.service.WebServerService
import io.github.nastechresearch.nastech.ui.components.nav.BackButton
import io.github.nastechresearch.nastech.ui.components.ui.permission.PermissionLocalNetwork
import io.github.nastechresearch.nastech.ui.components.ui.permission.PermissionManager
import io.github.nastechresearch.nastech.ui.components.ui.permission.PermissionNotification
import io.github.nastechresearch.nastech.ui.components.ui.permission.rememberPermissionState
import io.github.nastechresearch.nastech.ui.context.LocalSettings
import io.github.nastechresearch.nastech.ui.context.LocalToaster
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import io.github.nastechresearch.nastech.web.WebServerManager
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import kotlin.math.roundToInt
import org.koin.compose.koinInject

@Composable
fun SettingWebPage() {
    val webServerManager: WebServerManager = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings = LocalSettings.current
    val serverState by webServerManager.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val toaster = LocalToaster.current
    val copiedText = stringResource(R.string.copied)
    val settingsGlass = glassSurface(GlassSurface.SETTINGS, MaterialTheme.colorScheme.surfaceContainerHigh)

    var portText by remember(settings.webServerPort, serverState.isRunning) {
        mutableStateOf(settings.webServerPort.toString())
    }
    var passwordText by remember(settings.webServerAccessPassword, serverState.isRunning) {
        mutableStateOf(settings.webServerAccessPassword)
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }

    val permissionState = rememberPermissionState(
        permissions = buildSet {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionNotification)
            }
            if (Build.VERSION.SDK_INT >= 37 && !settings.webServerLocalhostOnly) {
                add(PermissionLocalNetwork)
            }
        },
    )
    PermissionManager(permissionState = permissionState)

    fun updateSettings(transform: (io.github.nastechresearch.nastech.data.datastore.Settings) -> io.github.nastechresearch.nastech.data.datastore.Settings) {
        scope.launch { settingsStore.update(transform) }
    }

    fun startServer() {
        val validPort = portText.toIntOrNull()?.takeIf { it in 1024..65535 } ?: settings.webServerPort
        if (validPort != settings.webServerPort) {
            updateSettings { it.copy(webServerPort = validPort) }
        }
        val intent = Intent(context, WebServerService::class.java).apply {
            action = WebServerService.ACTION_START
            putExtra(WebServerService.EXTRA_PORT, validPort)
            putExtra(WebServerService.EXTRA_LOCALHOST_ONLY, settings.webServerLocalhostOnly)
        }
        context.startForegroundService(intent)
        updateSettings { it.copy(webServerEnabled = true) }
    }

    fun stopServer() {
        context.startService(Intent(context, WebServerService::class.java).apply {
            action = WebServerService.ACTION_STOP
        })
        updateSettings { it.copy(webServerEnabled = false) }
    }

    fun copyAddress(address: String) {
        clipboardManager.setText(AnnotatedString(address))
        toaster.show(copiedText)
    }

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (pendingStart && permissionState.allPermissionsGranted) {
            pendingStart = false
            startServer()
        }
    }

    val localAddress = "http://127.0.0.1:${serverState.port}"
    val lanAddress = serverState.address?.let { "http://$it:${serverState.port}" }
    val mdnsAddress = serverState.hostname?.let { "http://$it:${serverState.port}" }
    val status = when {
        serverState.isLoading && serverState.isRunning -> "Stopping service"
        serverState.isLoading -> "Preparing secure access"
        serverState.isRunning -> if (serverState.localhostOnly) "Available on this device" else "Available on your local network"
        serverState.error != null -> "Needs attention"
        else -> "Offline"
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Web Access") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                WebAccessHero(
                    status = status,
                    running = serverState.isRunning,
                    loading = serverState.isLoading,
                    containerColor = settingsGlass.container,
                    borderColor = settingsGlass.border,
                    onPrimaryAction = {
                        if (serverState.isRunning) {
                            stopServer()
                        } else if (permissionState.allPermissionsGranted) {
                            startServer()
                        } else {
                            pendingStart = true
                            permissionState.requestPermissions()
                        }
                    },
                    onOpen = if (serverState.isRunning) {
                        {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(localAddress)))
                        }
                    } else {
                        null
                    },
                )
            }

            item { WebSectionLabel("Access") }
            item {
                WebAccessCard(
                    containerColor = settingsGlass.container,
                    borderColor = settingsGlass.border,
                ) {
                    WebAccessField(
                        title = "Port",
                        subtitle = "Choose the local port your browser will connect to.",
                    ) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { input ->
                                val digits = input.filter(Char::isDigit).take(5)
                                portText = digits
                                digits.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { port ->
                                    updateSettings { it.copy(webServerPort = port) }
                                }
                            },
                            enabled = !serverState.isRunning && !serverState.isLoading,
                            isError = portText.isNotBlank() && (portText.toIntOrNull() !in 1024..65535),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            supportingText = {
                                if (portText.isNotBlank() && portText.toIntOrNull() !in 1024..65535) {
                                    Text("Use a port from 1024 to 65535.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    WebAccessDivider()
                    WebAccessToggleRow(
                        title = "This device only",
                        subtitle = "Keep Web Access on 127.0.0.1. Turn this off only to use another device on the same network.",
                        checked = settings.webServerLocalhostOnly,
                        enabled = !serverState.isRunning && !serverState.isLoading,
                        onCheckedChange = { checked -> updateSettings { it.copy(webServerLocalhostOnly = checked) } },
                    )
                }
            }

            item { WebSectionLabel("Protection") }
            item {
                WebAccessCard(
                    containerColor = settingsGlass.container,
                    borderColor = settingsGlass.border,
                ) {
                    WebAccessToggleRow(
                        title = "Require a password",
                        subtitle = "Protect the browser interface before sharing it on a local network.",
                        checked = settings.webServerJwtEnabled,
                        enabled = !serverState.isRunning && !serverState.isLoading && (settings.webServerJwtEnabled || passwordText.isNotBlank()),
                        onCheckedChange = { enabled ->
                            updateSettings {
                                it.copy(
                                    webServerJwtEnabled = enabled,
                                    webServerAccessPassword = passwordText,
                                )
                            }
                        },
                    )
                    WebAccessDivider()
                    WebAccessField(
                        title = "Access password",
                        subtitle = "Saved only in your local Nastech settings.",
                    ) {
                        OutlinedTextField(
                            value = passwordText,
                            onValueChange = { value ->
                                passwordText = value
                                updateSettings {
                                    it.copy(
                                        webServerAccessPassword = value,
                                        webServerJwtEnabled = it.webServerJwtEnabled && value.isNotBlank(),
                                    )
                                }
                            },
                            enabled = !serverState.isRunning && !serverState.isLoading,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) HugeIcons.ViewOff else HugeIcons.View,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    )
                                }
                            },
                            singleLine = true,
                            isError = settings.webServerJwtEnabled && passwordText.isBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (serverState.isRunning) {
                item { WebSectionLabel("Active addresses") }
                item {
                    WebAccessCard(
                        containerColor = settingsGlass.container,
                        borderColor = settingsGlass.border,
                    ) {
                        WebAddressRow(
                            label = "This device",
                            address = localAddress,
                            onCopy = { copyAddress(localAddress) },
                        )
                        if (!serverState.localhostOnly && lanAddress != null) {
                            WebAccessDivider()
                            WebAddressRow(
                                label = "Local network",
                                address = lanAddress,
                                onCopy = { copyAddress(lanAddress) },
                            )
                        }
                        if (!serverState.localhostOnly && mdnsAddress != null) {
                            WebAccessDivider()
                            WebAddressRow(
                                label = "Network name",
                                address = mdnsAddress,
                                onCopy = { copyAddress(mdnsAddress) },
                            )
                        }
                    }
                }
            }

            item {
                WebSafetyNote(
                    message = if (settings.webServerLocalhostOnly) {
                        "Web Access is set to this device only. Start the service to browse your conversations locally."
                    } else {
                        "Local-network mode can be reached by devices on the same network. Use a password before you share an address."
                    },
                    containerColor = settingsGlass.container,
                    borderColor = settingsGlass.border,
                )
            }

            serverState.error?.let { error ->
                item {
                    WebSafetyNote(
                        message = error,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                        title = "Web Access could not start",
                    )
                }
            }
        }
    }
}

@Composable
private fun WebAccessHero(
    status: String,
    running: Boolean,
    loading: Boolean,
    containerColor: Color,
    borderColor: Color,
    onPrimaryAction: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Nastech in a browser", style = MaterialTheme.typography.headlineSmall)
            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Start a local web service to browse conversations and continue a chat from another device you control.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimaryAction,
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (running) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(if (running) HugeIcons.Stop else HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(if (running) "Stop" else "Start", modifier = Modifier.padding(start = 8.dp))
                }
                if (onOpen != null) {
                    TextButton(onClick = onOpen) { Text("Open") }
                }
            }
        }
    }
}

@Composable
private fun WebSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, top = 4.dp),
    )
}

@Composable
private fun WebAccessCard(
    containerColor: Color,
    borderColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun WebAccessField(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun WebAccessToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WebAddressRow(label: String, address: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onCopy) { Text("Copy") }
    }
}

@Composable
private fun WebSafetyNote(
    message: String,
    containerColor: Color,
    borderColor: Color,
    title: String = "Privacy note",
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WebAccessDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}
