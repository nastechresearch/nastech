package io.github.nastechresearch.nastech.ui.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dokar.sonner.ToastType
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.reliability.GitHubReleaseChecker
import io.github.nastechresearch.nastech.ui.context.LocalToaster
import io.github.nastechresearch.nastech.ui.theme.glassContentColor
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import io.github.nastechresearch.nastech.utils.UpdateDownload
import io.github.nastechresearch.nastech.utils.UpdateInstaller
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Foreground release check for the app's connected home surface. It deliberately performs one
 * public GitHub request when Home enters composition; no background polling, account data, or
 * silent installation is used. Android always controls the final package-install confirmation.
 */
@Composable
fun ReleaseUpdateNotice() {
    val checker = koinInject<GitHubReleaseChecker>()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val toaster = LocalToaster.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<GitHubReleaseChecker.CheckResult?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(checker) {
        result = checker.check()
    }
    DisposableEffect(lifecycleOwner, checker) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { result = checker.check() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val available = result as? GitHubReleaseChecker.CheckResult.Available ?: return
    if (dismissed) return

    val release = available.latest
    val apk = release.assets.firstOrNull {
        it.name.endsWith(".apk", ignoreCase = true) && it.browser_download_url.startsWith("https://")
    }
    val surface = glassSurface(GlassSurface.DIALOG, MaterialTheme.colorScheme.surfaceContainerHigh)
    val content = glassContentColor(GlassSurface.DIALOG, MaterialTheme.colorScheme.onSurface)

    Dialog(onDismissRequest = { dismissed = true }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = surface.container,
            contentColor = content,
            border = BorderStroke(1.dp, surface.border),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text("Update ready", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    release.name.ifBlank { release.tag_name },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "A new signed Nastech release is available. Download it here, then Android will ask you to confirm installation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.78f),
                )
                if (release.body.isNotBlank()) {
                    Text(
                        release.body.take(280),
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.64f),
                    )
                }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { dismissed = true }) { Text("Later") }
                    if (apk != null) {
                        Button(
                            onClick = {
                                val queued = UpdateInstaller.enqueue(
                                    context,
                                    UpdateDownload(
                                        name = apk.name,
                                        url = apk.browser_download_url,
                                        size = apk.size.toDisplaySize(),
                                    ),
                                ) != null
                                dismissed = true
                                toaster.show(
                                    if (queued) {
                                        "Update downloading. Nastech will show Install when Android has verified the APK."
                                    } else {
                                        "Unable to start the update download. Please try again."
                                    },
                                    type = if (queued) ToastType.Info else ToastType.Error,
                                )
                            },
                        ) {
                            Text("Download update")
                        }
                    } else if (release.html_url.startsWith("https://")) {
                        Button(onClick = { uriHandler.openUri(release.html_url) }) {
                            Text("Open release")
                        }
                    }
                }
            }
        }
    }
}

private fun Long.toDisplaySize(): String = when {
    this <= 0L -> "Signed APK"
    this < 1024L * 1024L -> "${this / 1024L} KB"
    else -> "${"%.1f".format(java.util.Locale.US, this / (1024f * 1024f))} MB"
}
