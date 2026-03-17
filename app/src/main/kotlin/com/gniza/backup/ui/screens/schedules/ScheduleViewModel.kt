package com.gniza.backup.ui.screens.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.repository.BackupLogRepository
import com.gniza.backup.data.repository.BackupSourceRepository
import com.gniza.backup.data.repository.ScheduleRepository
import com.gniza.backup.data.repository.ServerRepository
import com.gniza.backup.domain.model.BackupLog
import com.gniza.backup.domain.model.BackupSource
import com.gniza.backup.domain.model.Schedule
import com.gniza.backup.domain.model.Server
import com.gniza.backup.service.worker.BackupScheduler
import com.gniza.backup.ui.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ScheduleWithContext(
    val schedule: Schedule,
    val sourceName: String = "",
    val serverName: String = "",
    val lastLog: BackupLog? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val backupSourceRepository: BackupSourceRepository,
    private val serverRepository: ServerRepository,
    private val backupScheduler: BackupScheduler,
    private val backupLogRepository: BackupLogRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val schedulesWithContext: StateFlow<UiState<List<ScheduleWithContext>>> =
        combine(
            scheduleRepository.allSchedules,
            backupSourceRepository.allSources,
            serverRepository.allServers,
            backupLogRepository.allLogs
        ) { schedules, sources, servers, logs ->
            val sourceMap = sources.associateBy { it.id }
            val serverMap = servers.associateBy { it.id }
            val logsBySchedule = logs.groupBy { it.scheduleId }
            val result = schedules.map { schedule ->
                ScheduleWithContext(
                    schedule = schedule,
                    sourceName = sourceMap[schedule.sourceId]?.name ?: "Unknown source",
                    serverName = serverMap[schedule.serverId]?.name ?: "Unknown server",
                    lastLog = logsBySchedule[schedule.id]?.maxByOrNull { it.startedAt }
                )
            }
            UiState.Success(result) as UiState<List<ScheduleWithContext>>
        }
            .catch { emit(UiState.Error(it.message ?: "Failed to load schedules")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val sources: StateFlow<List<BackupSource>> = backupSourceRepository.allSources
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val servers: StateFlow<List<Server>> = serverRepository.allServers
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editSchedule = MutableStateFlow(Schedule())
    val editSchedule: StateFlow<Schedule> = _editSchedule.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun loadSchedule(id: Long) {
        if (id == 0L) {
            // Pre-fill destination path from QR setup if available
            val pathFile = java.io.File(context.filesDir, "qr_destination_path")
            val qrPath = if (pathFile.exists()) pathFile.readText().trim() else ""
            _editSchedule.value = Schedule(destinationPath = qrPath)
            return
        }
        viewModelScope.launch {
            val schedule = withContext(Dispatchers.IO) {
                scheduleRepository.getScheduleSync(id)
            }
            schedule?.let { _editSchedule.value = it }
        }
    }

    fun updateEditSchedule(schedule: Schedule) {
        _editSchedule.value = schedule
    }

    fun saveSchedule(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val schedule = _editSchedule.value.copy(
                updatedAt = System.currentTimeMillis()
            )
            val savedId = scheduleRepository.saveSchedule(schedule)
            val savedSchedule = schedule.copy(id = savedId)

            if (savedSchedule.enabled && savedSchedule.interval != com.gniza.backup.domain.model.ScheduleInterval.MANUAL) {
                backupScheduler.scheduleBackup(savedSchedule)
            } else {
                backupScheduler.cancelSchedule(savedId)
            }

            onSuccess()
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            backupScheduler.cancelSchedule(schedule.id)
            scheduleRepository.deleteSchedule(schedule)
        }
    }

    fun runNow(scheduleId: Long) {
        viewModelScope.launch {
            backupScheduler.runImmediate(scheduleId)
            _messages.emit("Backup started")
        }
    }

    fun stopBackup(scheduleId: Long) {
        viewModelScope.launch {
            backupScheduler.cancelImmediate(scheduleId)
            backupLogRepository.markStaleRunningAsFailed()
            _messages.emit("Backup stopped")
        }
    }
}
