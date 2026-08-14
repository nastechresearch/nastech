package io.github.nastechresearch.nastech.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.nastechresearch.nastech.NASTECH_VOICE_NOTIFICATION_CHANNEL_ID
import io.github.nastechresearch.nastech.R
import me.rerere.asr.LiveVoiceCallRegistry

private const val TAG = "NastechVoiceCallFgs"

/**
 * Gives a live ElevenLabs STS conversation an explicit Android foreground
 * identity. It does not implement telephony: the active microphone service and
 * voice-communication audio focus make the system-visible state accurate while
 * the controller owns the WebSocket and audio streams.
 */
class NastechVoiceCallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_CALL) {
            LiveVoiceCallRegistry.endActiveCall()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (!startForegroundCompat()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(): Boolean = runCatching {
        val notification = NotificationCompat.Builder(this, NASTECH_VOICE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(getString(R.string.notification_voice_call_title))
            .setContentText(getString(R.string.notification_voice_call_active))
            .setContentIntent(buildLaunchPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                getString(R.string.notification_voice_call_end),
                buildEndCallPendingIntent(),
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }.onFailure { error ->
        Log.w(TAG, "Unable to start Nastech Voice foreground service", error)
    }.isSuccess

    private fun buildLaunchPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        packageManager.getLaunchIntentForPackage(packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildEndCallPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, NastechVoiceCallService::class.java).setAction(ACTION_END_CALL),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val ACTION_START_CALL = "io.github.nastechresearch.nastech.action.START_VOICE_CALL"
        private const val ACTION_END_CALL = "io.github.nastechresearch.nastech.action.END_VOICE_CALL"
        private const val NOTIFICATION_ID = 2003

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context, NastechVoiceCallService::class.java).setAction(ACTION_START_CALL),
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to request Nastech Voice foreground service", error)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context, NastechVoiceCallService::class.java),
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to stop Nastech Voice foreground service", error)
            }
        }
    }
}
