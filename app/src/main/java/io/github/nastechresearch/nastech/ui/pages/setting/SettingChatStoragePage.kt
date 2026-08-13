package io.github.nastechresearch.nastech.ui.pages.setting

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.data.db.entity.ManagedFileEntity
import io.github.nastechresearch.nastech.data.files.FileFolders
import io.github.nastechresearch.nastech.data.files.FilesManager
import io.github.nastechresearch.nastech.ui.components.nav.BackButton
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.context.LocalToaster
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import io.github.nastechresearch.nastech.utils.fileSizeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

enum class NastechStorageFilter(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    DOCUMENTS("Documents"),
    AUDIO("Audio"),
    VIDEO("Video"),
}

private data class NastechStorageSnapshot(
    val appPackageBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val databaseBytes: Long = 0L,
    val attachmentBytes: Long = 0L,
    val attachmentCount: Int = 0,
    val imageBytes: Long = 0L,
    val workspaceBytes: Long = 0L,
    val skillBytes: Long = 0L,
    val toolOutputBytes: Long = 0L,
    val fontBytes: Long = 0L,
    val browserBytes: Long = 0L,
) {
    val totalBytes: Long
        get() = appPackageBytes + dataBytes + cacheBytes + databaseBytes
}

@Composable
fun SettingChatStoragePage(
    filesManager: FilesManager = koinInject(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navigator = LocalNavController.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val attachments by filesManager.observe(FileFolders.UPLOAD)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val settingsGlass = glassSurface(GlassSurface.SETTINGS, MaterialTheme.colorScheme.surfaceContainerHigh)

    var scanRequest by rememberSaveable { mutableIntStateOf(0) }
    var storage by remember { mutableStateOf(NastechStorageSnapshot()) }
    var filter by rememberSaveable { mutableStateOf(NastechStorageFilter.ALL) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scanRequest, attachments.size) {
        storage = withContext(Dispatchers.IO) {
            scanNastechStorage(context, attachments)
        }
    }

    val visibleAttachments = remember(attachments, filter) {
        attachments
            .filter { attachment -> filter.matches(attachment) }
            .sortedWith(compareByDescending<ManagedFileEntity> { it.updatedAt }.thenByDescending { it.sizeBytes })
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear temporary cache?") },
            text = {
                Text(
                    "This removes temporary browser, preview, and cached media files. Your conversations, managed attachments, backups, and workspace files are not deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clearDirectoryContents(context.cacheDir)
                        showClearCacheDialog = false
                        scanRequest += 1
                        toaster.show("Temporary cache cleared")
                    }
                ) { Text("Clear cache") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Chat Storage") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StorageHeroCard(
                    snapshot = storage,
                    containerColor = settingsGlass.container,
                    borderColor = settingsGlass.border,
                    onRefresh = { scanRequest += 1 },
                )
            }

            item { StorageSectionLabel("App storage") }
            item {
                StorageCard(settingsGlass.container, settingsGlass.border) {
                    StorageCategoryRow(
                        "Chat attachments",
                        "${storage.attachmentCount} managed files",
                        storage.attachmentBytes,
                        onClick = { navigator.navigate(Screen.SettingFiles) },
                    )
                    StorageDivider()
                    StorageCategoryRow(
                        "Generated media",
                        "Images saved by Nastech tools and responses",
                        storage.imageBytes,
                    )
                    StorageDivider()
                    StorageCategoryRow(
                        "Workspaces",
                        "Project files and local workspace environments",
                        storage.workspaceBytes,
                    )
                    StorageDivider()
                    StorageCategoryRow(
                        "Skills and tool output",
                        "Installed skills and generated tool artifacts",
                        storage.skillBytes + storage.toolOutputBytes,
                    )
                    StorageDivider()
                    StorageCategoryRow(
                        "Custom fonts and browser profile",
                        "Appearance resources and local browser data",
                        storage.fontBytes + storage.browserBytes,
                    )
                    StorageDivider()
                    StorageCategoryRow(
                        "Nastech database",
                        "Conversations, assistant data, settings, and indexes",
                        storage.databaseBytes,
                        onClick = { navigator.navigate(Screen.Backup) },
                    )
                }
            }

            item { StorageSectionLabel("Attachment library") }
            item {
                StorageCard(settingsGlass.container, settingsGlass.border) {
                    AttachmentFilterRow(selected = filter, onSelect = { filter = it })
                    StorageDivider()
                    if (visibleAttachments.isEmpty()) {
                        Text(
                            text = if (attachments.isEmpty()) "No managed chat attachments yet." else "No attachments match this filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    } else {
                        visibleAttachments.take(6).forEachIndexed { index, attachment ->
                            if (index > 0) StorageDivider()
                            AttachmentRow(attachment)
                        }
                        if (visibleAttachments.size > 6) StorageDivider()
                        TextButton(
                            onClick = { navigator.navigate(Screen.SettingFiles) },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text("Manage all ${visibleAttachments.size} attachments")
                        }
                    }
                }
            }

            item { StorageSectionLabel("Storage controls") }
            item {
                StorageCard(settingsGlass.container, settingsGlass.border) {
                    StorageActionRow(
                        title = "Clear temporary cache",
                        subtitle = "Free ${storage.cacheBytes.fileSizeToString()} from temporary previews and browser cache without removing chats or files.",
                        action = "Review",
                        onClick = { showClearCacheDialog = true },
                    )
                    StorageDivider()
                    StorageActionRow(
                        title = "Back up your Nastech data",
                        subtitle = "Use the existing secure backup flow before moving or resetting data.",
                        action = "Open backup",
                        onClick = { navigator.navigate(Screen.Backup) },
                    )
                    StorageDivider()
                    StorageActionRow(
                        title = "Refresh storage scan",
                        subtitle = "Recalculate on-device usage after adding, exporting, or deleting files.",
                        action = "Refresh",
                        onClick = { scanRequest += 1 },
                    )
                }
            }

            item {
                StorageNoteCard(
                    containerColor = settingsGlass.container,
                    borderColor = settingsGlass.border,
                )
            }
        }
    }
}

@Composable
private fun StorageHeroCard(
    snapshot: NastechStorageSnapshot,
    containerColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
    onRefresh: () -> Unit,
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nastech storage", style = MaterialTheme.typography.headlineSmall)
            Text(
                snapshot.totalBytes.fileSizeToString(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Measured on this device. Includes the installed app package, app data, database, and temporary cache.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoragePill("App ${snapshot.appPackageBytes.fileSizeToString()}")
                StoragePill("Data ${snapshot.dataBytes.fileSizeToString()}")
                StoragePill("Cache ${snapshot.cacheBytes.fileSizeToString()}")
            }
            OutlinedButton(onClick = onRefresh) { Text("Refresh scan") }
        }
    }
}

@Composable
private fun StoragePill(text: String) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

@Composable
private fun StorageSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, top = 4.dp),
    )
}

@Composable
private fun StorageCard(
    containerColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun StorageCategoryRow(
    title: String,
    subtitle: String,
    bytes: Long,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(bytes.fileSizeToString(), style = MaterialTheme.typography.titleSmall)
            if (onClick != null) TextButton(onClick = onClick) { Text("Open") }
        }
    }
}

@Composable
private fun AttachmentFilterRow(selected: NastechStorageFilter, onSelect: (NastechStorageFilter) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Filter attachments", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            NastechStorageFilter.entries.take(3).forEach { option ->
                FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            NastechStorageFilter.entries.drop(3).forEach { option ->
                FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option.label) })
            }
        }
    }
}

@Composable
private fun AttachmentRow(attachment: ManagedFileEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(attachment.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(attachment.mimeType, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(attachment.sizeBytes.fileSizeToString(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StorageActionRow(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun StorageNoteCard(containerColor: androidx.compose.ui.graphics.Color, borderColor: androidx.compose.ui.graphics.Color) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Your data stays under your control", style = MaterialTheme.typography.titleMedium)
            Text(
                "Storage measurements are local to this device. Manage attachments individually, clear only temporary cache here, and use Backup before moving or resetting persistent Nastech data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

private fun NastechStorageFilter.matches(file: ManagedFileEntity): Boolean = when (this) {
    NastechStorageFilter.ALL -> true
    NastechStorageFilter.IMAGES -> file.mimeType.startsWith("image/")
    NastechStorageFilter.DOCUMENTS -> file.mimeType.startsWith("text/") ||
        file.mimeType.contains("pdf") || file.mimeType.contains("document") || file.mimeType.contains("sheet")
    NastechStorageFilter.AUDIO -> file.mimeType.startsWith("audio/")
    NastechStorageFilter.VIDEO -> file.mimeType.startsWith("video/")
}

private fun scanNastechStorage(context: Context, attachments: List<ManagedFileEntity>): NastechStorageSnapshot {
    val filesDir = context.filesDir
    val databaseBytes = context.databaseList().sumOf { name ->
        File(context.getDatabasePath(name).absolutePath).length() +
            File(context.getDatabasePath(name).absolutePath + "-wal").length() +
            File(context.getDatabasePath(name).absolutePath + "-shm").length()
    }
    return NastechStorageSnapshot(
        appPackageBytes = runCatching { File(context.applicationInfo.sourceDir).length() }.getOrDefault(0L),
        dataBytes = directorySize(filesDir),
        cacheBytes = directorySize(context.cacheDir),
        databaseBytes = databaseBytes,
        attachmentBytes = attachments.sumOf { it.sizeBytes },
        attachmentCount = attachments.size,
        imageBytes = directorySize(File(filesDir, FileFolders.IMAGES)),
        workspaceBytes = directorySize(File(filesDir, "workspaces")),
        skillBytes = directorySize(File(filesDir, FileFolders.SKILLS)),
        toolOutputBytes = directorySize(File(filesDir, FileFolders.TOOL_OUTPUTS)),
        fontBytes = directorySize(File(filesDir, FileFolders.FONTS)),
        browserBytes = directorySize(File(filesDir, "browser-profile")),
    )
}

private fun directorySize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf(::directorySize) ?: 0L
}

private fun clearDirectoryContents(directory: File) {
    directory.listFiles()?.forEach { child -> child.deleteRecursively() }
}
