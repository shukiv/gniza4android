package com.gniza.backup.service.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gniza.backup.domain.model.Schedule
import com.gniza.backup.domain.model.ScheduleInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scheduleBackup(schedule: Schedule) {
        val workManager = WorkManager.getInstance(context)

        val (intervalMinutes, intervalUnit) = when (schedule.interval) {
            ScheduleInterval.HOURLY -> 15L to TimeUnit.MINUTES // WorkManager minimum is 15 min
            ScheduleInterval.DAILY -> 24L to TimeUnit.HOURS
            ScheduleInterval.WEEKLY -> 7L to TimeUnit.DAYS
            ScheduleInterval.MANUAL -> return // No scheduling for manual
        }

        val constraints = Constraints.Builder().apply {
            if (schedule.wifiOnly) {
                setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
            setRequiresCharging(schedule.whileCharging)
        }.build()

        val inputData = workDataOf(
            BackupWorker.KEY_SCHEDULE_ID to schedule.id
        )

        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(intervalMinutes, intervalUnit)
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(workName(schedule.id))
            .build()

        workManager.enqueueUniquePeriodicWork(
            workName(schedule.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelSchedule(scheduleId: Long) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(workName(scheduleId))
    }

    fun runImmediate(scheduleId: Long) {
        val workManager = WorkManager.getInstance(context)

        val inputData = workDataOf(
            BackupWorker.KEY_SCHEDULE_ID to scheduleId
        )

        val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            "backup_immediate_$scheduleId",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelImmediate(scheduleId: Long) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("backup_immediate_$scheduleId")
    }

    private fun workName(scheduleId: Long) = "backup_$scheduleId"
}
