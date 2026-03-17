package com.gniza.backup.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {

    const val BACKUP_CHANNEL_ID = "backup_progress_v2"
    private const val LEGACY_CHANNEL_ID = "backup_progress"
    private const val BACKUP_CHANNEL_NAME = "Backup Progress"
    private const val BACKUP_CHANNEL_DESCRIPTION = "Shows backup progress and completion notifications"

    fun createAll(context: Context) {
        val notificationManager =
            context.getSystemService(NotificationManager::class.java)

        // Remove legacy channel — Android caches channel importance from first creation,
        // so upgrading IMPORTANCE_LOW to IMPORTANCE_DEFAULT requires a new channel ID.
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)

        val channel = NotificationChannel(
            BACKUP_CHANNEL_ID,
            BACKUP_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = BACKUP_CHANNEL_DESCRIPTION
        }

        notificationManager.createNotificationChannel(channel)
    }
}
