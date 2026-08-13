package io.github.nastechresearch.nastech.utils

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.ui.activity.UpdateInstallActivity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Handles only user-visible APK updates from the configured release feed. The APK is retained in
 * app-owned external storage, Android validates the signing identity during installation, and the
 * final package-installer confirmation always belongs to the user.
 */
object UpdateInstaller {
    private const val PREFS = "nastech_update_installer"
    private const val DOWNLOAD_ID = "download_id"
    private const val CHANNEL_ID = "downloaded_updates"
    const val EXTRA_DOWNLOAD_ID = "update_download_id"

    fun enqueue(context: Context, download: UpdateDownload): Long? {
        val url = download.url.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || !download.name.endsWith(".apk", ignoreCase = true)) return null
        val request = DownloadManager.Request(download.url.toUri()).apply {
            setTitle(download.name)
            setDescription(context.getString(R.string.update_download_description))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "updates/${download.name}",
            )
            setMimeType("application/vnd.android.package-archive")
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return runCatching {
            manager.enqueue(request).also { id ->
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(DOWNLOAD_ID, id)
                    .apply()
            }
        }.getOrNull()
    }

    fun installDownloaded(context: Context, downloadId: Long): InstallRequestResult {
        val uri = downloadedUri(context, downloadId) ?: return InstallRequestResult.NOT_READY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallRequestResult.SOURCE_PERMISSION_REQUIRED
        }
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(installIntent)
            InstallRequestResult.INSTALLER_OPENED
        }.getOrElse { InstallRequestResult.NOT_READY }
    }

    fun mostRecentDownloadId(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(DOWNLOAD_ID, -1L)

    fun downloadedUri(context: Context, downloadId: Long): Uri? {
        if (downloadId < 0) return null
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        manager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return if (status == DownloadManager.STATUS_SUCCESSFUL) manager.getUriForDownloadedFile(downloadId) else null
        }
    }

    fun publishInstallReadyNotification(context: Context, downloadId: Long) {
        if (downloadedUri(context, downloadId) == null) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Nastech updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Download completion and user-confirmed installation prompts."
                },
            )
        }
        val installIntent = Intent(context, UpdateInstallActivity::class.java)
            .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            downloadId.toInt(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Nastech update ready")
                .setContentText("Tap Install to let Android verify and install the downloaded update.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }
}

enum class InstallRequestResult {
    INSTALLER_OPENED,
    SOURCE_PERMISSION_REQUIRED,
    NOT_READY,
}

class UpdateDownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (completedId == UpdateInstaller.mostRecentDownloadId(context)) {
            UpdateInstaller.publishInstallReadyNotification(context, completedId)
        }
    }
}
