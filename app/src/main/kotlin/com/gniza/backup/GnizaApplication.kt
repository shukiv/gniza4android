package com.gniza.backup

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gniza.backup.data.repository.ScheduleRepository
import com.gniza.backup.service.worker.BackupScheduler
import com.gniza.backup.util.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GnizaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject lateinit var backupScheduler: BackupScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "GNIZA/${super.createStackElementTag(element)}"
                }
            })
        }
        // Register EdDSA provider for ed25519 SSH key support in JSch
        java.security.Security.addProvider(net.i2p.crypto.eddsa.EdDSASecurityProvider())
        NotificationChannels.createAll(this)

        // Reschedule all enabled backups on app startup
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val schedules = scheduleRepository.getEnabledSchedules()
                schedules.forEach { schedule ->
                    backupScheduler.scheduleBackup(schedule)
                }
                Timber.d("App startup: rescheduled %d backup(s)", schedules.size)
            } catch (e: Exception) {
                Timber.w(e, "Failed to reschedule backups on app startup")
            }
        }
    }
}
