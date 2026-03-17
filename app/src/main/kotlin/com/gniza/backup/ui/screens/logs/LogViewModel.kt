package com.gniza.backup.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.preferences.AppPreferences
import com.gniza.backup.data.repository.BackupLogRepository
import com.gniza.backup.domain.model.BackupLog
import com.gniza.backup.domain.model.BackupStatus
import com.gniza.backup.ui.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LogFilter {
    ALL, SUCCESS, FAILED
}

@HiltViewModel
class LogViewModel @Inject constructor(
    private val backupLogRepository: BackupLogRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    val logs: StateFlow<UiState<List<BackupLog>>> =
        combine(
            backupLogRepository.allLogs,
            _filter
        ) { allLogs, currentFilter ->
            val filtered = when (currentFilter) {
                LogFilter.ALL -> allLogs
                LogFilter.SUCCESS -> allLogs.filter { it.status == BackupStatus.SUCCESS }
                LogFilter.FAILED -> allLogs.filter { it.status == BackupStatus.FAILED }
            }
            val sorted = filtered.sortedByDescending { it.startedAt }
            UiState.Success(sorted) as UiState<List<BackupLog>>
        }
            .catch { emit(UiState.Error(it.message ?: "Failed to load logs")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun setFilter(filter: LogFilter) {
        _filter.value = filter
    }

    fun deleteOldLogs() {
        viewModelScope.launch {
            val retentionDays = appPreferences.logRetentionDays.first()
            backupLogRepository.deleteOldLogs(retentionDays)
        }
    }

    fun deleteAllLogs() {
        viewModelScope.launch {
            backupLogRepository.deleteAllLogs()
        }
    }
}
