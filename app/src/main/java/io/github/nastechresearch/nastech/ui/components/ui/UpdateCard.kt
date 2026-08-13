package io.github.nastechresearch.nastech.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Download01
import io.github.nastechresearch.nastech.BuildConfig
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.ui.components.richtext.MarkdownBlock
import io.github.nastechresearch.nastech.ui.context.LocalToaster
import io.github.nastechresearch.nastech.ui.theme.glassContentColor
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import io.github.nastechresearch.nastech.ui.hooks.useThrottle
import io.github.nastechresearch.nastech.ui.pages.chat.ChatVM
import io.github.nastechresearch.nastech.utils.UpdateDownload
import io.github.nastechresearch.nastech.utils.Version
import io.github.nastechresearch.nastech.utils.onError
import io.github.nastechresearch.nastech.utils.onSuccess
import io.github.nastechresearch.nastech.utils.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
@Composable
fun UpdateCard(vm: ChatVM, compact: Boolean = false) {
    val state by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    state.onError {
        val alertSurface = glassSurface(GlassSurface.ACTIVITY, MaterialTheme.colorScheme.surfaceContainerHigh)
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, alertSurface.border),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = alertSurface.container,
                contentColor = glassContentColor(GlassSurface.ACTIVITY, MaterialTheme.colorScheme.surfaceContainerHigh),
            ),
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_card_check_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = it.message ?: stringResource(R.string.update_card_unknown_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    state.onSuccess { info ->
        var showDetail by remember { mutableStateOf(false) }
        var dismissed by remember { mutableStateOf(false) }
        val current = remember { Version(BuildConfig.VERSION_NAME) }
        val latest = remember(info) { Version(info.version) }
        if (latest > current && !dismissed) {
            val updateSurface = glassSurface(GlassSurface.ACTIVITY, MaterialTheme.colorScheme.surfaceContainerHigh)
            Card(
                onClick = {
                    showDetail = true
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, updateSurface.border),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = updateSurface.container,
                    contentColor = glassContentColor(GlassSurface.ACTIVITY, MaterialTheme.colorScheme.surfaceContainerHigh),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.update_card_new_version_found, info.version),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { dismissed = true }) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = stringResource(R.string.update_card_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (compact) {
                        Text(
                            text = "Tap to review release notes and download the signed update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MarkdownBlock(
                            content = info.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 200.dp),
                        )
                    }
                }
            }
        }
        if (showDetail) {
            val downloadHandler = useThrottle<UpdateDownload>(500) { item ->
                val queued = vm.updateChecker.downloadUpdate(context, item)
                showDetail = false
                toaster.show(
                    if (queued) "Update downloading. Nastech will show Install when Android has the signed APK ready."
                    else "Unable to start the update download. Please try again.",
                    type = if (queued) ToastType.Info else ToastType.Error,
                )
            }
            ModalBottomSheet(
                onDismissRequest = { showDetail = false },
                sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = info.version,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = Instant.parse(info.publishedAt).toJavaInstant().toLocalDateTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MarkdownBlock(
                        content = info.changelog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    info.downloads.fastForEach { downloadItem ->
                        OutlinedCard(
                            onClick = {
                                downloadHandler(downloadItem)
                            },
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "Download ${downloadItem.name}",
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${downloadItem.size} · Android will ask you to confirm installation"
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = HugeIcons.Download01,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
