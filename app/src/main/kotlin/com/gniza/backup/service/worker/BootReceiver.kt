package com.gniza.backup.service.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gniza.backup.data.repository.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject lateinit var backupScheduler: BackupScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedules = withTimeout(25_000) {
                    scheduleRepository.getEnabledSchedules()
                }
                schedules.forEach { schedule ->
                    backupScheduler.scheduleBackup(schedule)
                    Timber.d("Rescheduled backup for schedule: %s (id=%d)", schedule.name, schedule.id)
                }
                Timber.i("Boot receiver rescheduled %d backup(s)", schedules.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to reschedule backups after boot")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
