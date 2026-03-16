package com.gniza.backup.service.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gniza.backup.data.repository.BackupLogRepository
import com.gniza.backup.data.repository.BackupSourceRepository
import com.gniza.backup.data.repository.ScheduleRepository
import com.gniza.backup.service.backup.BackupExecutor
import com.gniza.backup.service.backup.BackupNotificationManager
import com.gniza.backup.service.rsync.RsyncOutput
import com.gniza.backup.util.FileUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupExecutor: BackupExecutor,
    private val scheduleRepository: ScheduleRepository,
    private val backupSourceRepository: BackupSourceRepository,
    private val backupNotificationManager: BackupNotificationManager,
    private val backupLogRepository: BackupLogRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SCHEDULE_ID = "schedule_id"
    }

    override suspend fun doWork(): Result {
        backupLogRepository.markStaleRunningAsFailed()

        val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1)
        if (scheduleId == -1L) {
            return Result.failure()
        }

        val schedule = scheduleRepository.getScheduleSync(scheduleId)
            ?: return Result.failure()

        val source = backupSourceRepository.getSourceSync(schedule.sourceId)
            ?: return Result.failure()

        val notification = backupNotificationManager.createProgressNotification(
            sourceName = source.name,
            progress = "Starting backup..."
        )
        setForeground(ForegroundInfo(BackupNotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))

        val result = backupExecutor.execute(source, schedule) { output ->
            when (output) {
                is RsyncOutput.Progress -> {
                    val progressNotification = backupNotificationManager.createProgressNotification(
                        sourceName = source.name,
                        progress = "${output.fileName} - ${output.percentage}% (${output.speed})"
                    )
                    setForegroundAsync(
                        ForegroundInfo(BackupNotificationManager.NOTIFICATION_ID, progressNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    )
                }
                else -> { /* Other events handled by executor */ }
            }
        }

        val message = if (result.success) {
            "${result.filesTransferred} files, ${FileUtils.formatBytes(result.bytesTransferred)} in ${FileUtils.formatDuration(result.durationSeconds)}"
        } else {
            result.errorMessage ?: "Unknown error"
        }

        backupNotificationManager.showCompletionNotification(
            sourceName = source.name,
            success = result.success,
            message = message
        )

        return if (result.success) Result.success() else Result.failure()
    }
}
