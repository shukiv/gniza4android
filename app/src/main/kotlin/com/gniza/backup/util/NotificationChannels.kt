package com.gniza.backup.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {

    private const val BACKUP_CHANNEL_ID = "backup_progress"
    private const val BACKUP_CHANNEL_NAME = "Backup Progress"
    private const val BACKUP_CHANNEL_DESCRIPTION = "Shows backup progress and completion notifications"

    fun createAll(context: Context) {
        val channel = NotificationChannel(
            BACKUP_CHANNEL_ID,
            BACKUP_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = BACKUP_CHANNEL_DESCRIPTION
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
