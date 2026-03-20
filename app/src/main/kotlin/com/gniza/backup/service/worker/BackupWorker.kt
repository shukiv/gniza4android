package com.gniza.backup.service.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gniza.backup.data.preferences.AppPreferences
import com.gniza.backup.data.repository.BackupLogRepository
import com.gniza.backup.data.repository.BackupSourceRepository
import com.gniza.backup.data.repository.ScheduleRepository
import com.gniza.backup.service.backup.BackupExecutor
import com.gniza.backup.service.backup.BackupNotificationManager
import com.gniza.backup.service.rsync.RsyncOutput
import com.gniza.backup.util.FileUtils
import kotlinx.coroutines.flow.first
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupExecutor: BackupExecutor,
    private val scheduleRepository: ScheduleRepository,
    private val backupSourceRepository: BackupSourceRepository,
    private val backupNotificationManager: BackupNotificationManager,
    private val backupLogRepository: BackupLogRepository,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SCHEDULE_ID = "schedule_id"
        private const val NOTIFICATION_THROTTLE_MS = 300L

        // Per-schedule mutex to prevent concurrent runs of the same schedule
        private val scheduleLocks = ConcurrentHashMap<Long, Mutex>()

        private fun lockFor(scheduleId: Long): Mutex =
            scheduleLocks.getOrPut(scheduleId) { Mutex() }
    }

    private fun buildForegroundInfo(notification: Notification): ForegroundInfo =
        ForegroundInfo(
            BackupNotificationManager.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = backupNotificationManager.createProgressNotification(
            sourceName = "Gniza",
            progress = "Preparing backup..."
        )
        return buildForegroundInfo(notification)
    }

    override suspend fun doWork(): Result {
        // Immediately promote to foreground before doing ANY work
        try {
            val initialNotification = backupNotificationManager.createProgressNotification(
                sourceName = "Gniza",
                progress = "Starting backup..."
            )
            setForeground(buildForegroundInfo(initialNotification))
        } catch (e: IllegalStateException) {
            Timber.e(e, "Failed to promote to foreground service; will retry later")
            if (runAttemptCount > 3) {
                Timber.e("Failed to promote to foreground after %d attempts; giving up", runAttemptCount)
                return Result.failure()
            }
            return Result.retry()
        }

        backupLogRepository.markStaleRunningAsFailed()

        val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1)
        if (scheduleId == -1L) {
            Timber.e("BackupWorker launched without a valid schedule_id in inputData")
            return Result.failure()
        }

        // Per-schedule lock: if this schedule is already running, skip
        val lock = lockFor(scheduleId)
        if (!lock.tryLock()) {
            Timber.d("Backup for schedule $scheduleId already running, skipping")
            return Result.success()
        }

        try {
            return executeBackup(scheduleId)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun executeBackup(scheduleId: Long): Result {
        val schedule = scheduleRepository.getScheduleSync(scheduleId)
        if (schedule == null) {
            Timber.w("Schedule %d not found (possibly deleted), aborting backup", scheduleId)
            return Result.failure()
        }

        val source = backupSourceRepository.getSourceSync(schedule.sourceId)
        if (source == null) {
            Timber.w("Source %d for schedule %d not found, aborting backup", schedule.sourceId, scheduleId)
            return Result.failure()
        }

        val notification = backupNotificationManager.createProgressNotification(
            sourceName = source.name,
            progress = "Starting backup..."
        )
        try {
            setForeground(buildForegroundInfo(notification))
        } catch (e: IllegalStateException) {
            Timber.e(e, "Failed to promote to foreground for source: %s", source.name)
        }

        var lastNotificationUpdateMs = 0L

        val result = backupExecutor.execute(source, schedule) { output ->
            when (output) {
                is RsyncOutput.Progress -> {
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationUpdateMs >= NOTIFICATION_THROTTLE_MS) {
                        lastNotificationUpdateMs = now
                        val progressNotification = backupNotificationManager.createProgressNotification(
                            sourceName = source.name,
                            progress = "${output.fileName} - ${output.percentage}% (${output.speed})",
                            percentage = output.percentage
                        )
                        setForegroundAsync(buildForegroundInfo(progressNotification))
                    }
                }
                is RsyncOutput.FileComplete,
                is RsyncOutput.Summary,
                is RsyncOutput.Error,
                is RsyncOutput.Log -> Unit // Handled by executor
            }
        }

        val message = if (result.success) {
            "${result.filesTransferred} files, ${FileUtils.formatBytes(result.bytesTransferred)} in ${FileUtils.formatDuration(result.durationSeconds)}"
        } else {
            sanitizeErrorForNotification(result.errorMessage ?: "Unknown error")
        }

        backupNotificationManager.showCompletionNotification(
            sourceName = source.name,
            success = result.success,
            message = message
        )

        // Auto-cleanup old logs based on retention setting
        try {
            val retentionDays = appPreferences.logRetentionDays.first()
            backupLogRepository.deleteOldLogs(retentionDays)
        } catch (e: Exception) {
            Timber.w(e, "Failed to cleanup old logs")
        }

        return when {
            result.success -> Result.success()
            isTransientError(result.errorMessage) -> Result.retry()
            else -> Result.failure()
        }
    }

    private fun isTransientError(errorMessage: String?): Boolean {
        if (errorMessage == null) return false
        val transientKeywords = listOf(
            "connection", "timeout", "network", "refused", "unreachable",
            "IOException", "EHOSTUNREACH", "ENETUNREACH", "ECONNREFUSED",
            "ECONNRESET", "connection reset", "No route to host"
        )
        return transientKeywords.any { errorMessage.contains(it, ignoreCase = true) }
    }

    private fun sanitizeErrorForNotification(error: String): String {
        return when {
            "Permission denied" in error -> "Authentication failed"
            "Connection refused" in error -> "Server unreachable"
            "No route to host" in error -> "Network error"
            "timeout" in error.lowercase() -> "Connection timed out"
            "No space left" in error -> "No space left on server"
            "Host key verification" in error -> "Server identity changed"
            else -> "Backup failed (see logs for details)"
        }
    }
}
