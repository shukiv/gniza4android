package com.gniza.backup.service.backup

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.gniza.backup.R
import com.gniza.backup.util.NotificationChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun createProgressNotification(sourceName: String, progress: String, percentage: Int = 0): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Backing up: $sourceName")
            .setContentText(progress)
            .setProgress(100, percentage.coerceIn(0, 100), percentage == 0)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, NotificationChannels.BACKUP_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Backup in progress")
                    .setProgress(100, percentage.coerceIn(0, 100), percentage == 0)
                    .build()
            )
            .build()
    }

    fun showCompletionNotification(sourceName: String, success: Boolean, message: String) {
        val title = if (success) "Backup complete" else "Backup failed"
        val notification = NotificationCompat.Builder(context, NotificationChannels.BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$title: $sourceName")
            .setContentText(message)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, NotificationChannels.BACKUP_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .build()
            )
            .build()

        // Use timestamp-based ID so concurrent completions don't overwrite each other.
        // Offset by 2000 to avoid collision with NOTIFICATION_ID (1001).
        val notificationId = 2000 + (System.currentTimeMillis() % 100_000).toInt()
        notificationManager.notify(notificationId, notification)
    }
}
