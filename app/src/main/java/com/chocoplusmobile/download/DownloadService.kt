package com.chocoplusmobile.download

import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.chocoplusmobile.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.text

/**
 * Foreground service whose only job is keeping the process alive (and
 * showing progress in a notification) while DownloadManager has active
 * downloads — the downloads themselves run in DownloadManager's own scope.
 * Without it, Android is free to kill the app a few minutes after it leaves
 * the screen, which a multi-GB movie download would rarely survive.
 * Stops itself as soon as nothing is queued or downloading any more.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(DownloadManager.entries.value.values.filter { it.isActive }),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (!observing) {
            observing = true
            scope.launch {
                DownloadManager.entries.collectLatest { map ->
                    val active = map.values.filter { it.isActive }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(active))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    // Android 15+ caps dataSync services at 6h per day — downloads keep
    // going while the app stays in memory, just without the guarantee.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, Tr.PAGE_DOWNLOADS.text(), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(active: List<DownloadEntry>): android.app.Notification {
        val running = active.filter { it.status == DownloadStatus.DOWNLOADING }
        val title = when (active.size) {
            0 -> Tr.PAGE_DOWNLOADS.text()
            1 -> Tr.DOWNLOAD_NOTIF_ONE.text(active.first().displayTitle())
            else -> Tr.DOWNLOAD_NOTIF_MANY.text(active.size)
        }
        val known = running.filter { it.totalBytes > 0 }
        val percent = if (known.isNotEmpty()) ((known.sumOf { it.bytesDownloaded } * 100) / known.sumOf { it.totalBytes }).toInt() else null
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(percent?.let { Tr.PERCENT.text(it) } ?: Tr.DOWNLOAD_STATUS_QUEUED.text())
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses to start a foreground service while the
                // app is itself in the background — downloads still run, just
                // without the keep-alive until the next start from the UI.
                Log.w("DownloadService", "Could not start the download service", e)
            }
        }
    }
}

/** Title shown for a download wherever there's no room for artwork. */
fun DownloadEntry.displayTitle(): String {
    val mediaTitle = media?.title ?: ""
    return when (kind) {
        DownloadKind.MOVIE -> mediaTitle.ifBlank { Tr.MOVIE.text() }
        DownloadKind.EPISODE -> listOf(mediaTitle, episode?.name ?: "").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { Tr.EPISODE.text() }
    }
}
