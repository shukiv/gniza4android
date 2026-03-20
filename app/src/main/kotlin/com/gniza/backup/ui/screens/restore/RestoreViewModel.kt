package com.gniza.backup.ui.screens.restore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.repository.ScheduleRepository
import com.gniza.backup.data.repository.ServerRepository
import com.gniza.backup.domain.model.RemoteFileEntry
import com.gniza.backup.domain.model.Server
import com.gniza.backup.domain.model.ServerType
import com.gniza.backup.domain.model.Snapshot
import com.gniza.backup.service.restore.RestoreService
import com.gniza.backup.service.rsync.RsyncOutput
import com.gniza.backup.ui.util.UiState
import com.gniza.backup.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val restoreService: RestoreService,
    private val scheduleRepository: ScheduleRepository,
    private val serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val scheduleId: Long = savedStateHandle["scheduleId"] ?: 0L
    val snapshotNameArg: String? = savedStateHandle["snapshotName"]

    private val _snapshots = MutableStateFlow<UiState<List<Snapshot>>>(UiState.Loading)
    val snapshots: StateFlow<UiState<List<Snapshot>>> = _snapshots.asStateFlow()

    private val _files = MutableStateFlow<UiState<List<RemoteFileEntry>>>(UiState.Loading)
    val files: StateFlow<UiState<List<RemoteFileEntry>>> = _files.asStateFlow()

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _restoreState = MutableStateFlow(RestoreProgressState())
    val restoreState: StateFlow<RestoreProgressState> = _restoreState.asStateFlow()

    private var server: Server? = null
    private var destPath: String = ""
    var serverType: ServerType = ServerType.SSH
        private set
    var isSnapshotMode: Boolean = true
        private set
    private val flatNavigationStack = mutableListOf<String>()

    init {
        loadScheduleContext()
    }

    private fun loadScheduleContext() {
        viewModelScope.launch {
            val schedule = scheduleRepository.getScheduleSync(scheduleId) ?: run {
                _snapshots.value = UiState.Error("Schedule not found")
                return@launch
            }
            destPath = schedule.destinationPath
            server = serverRepository.getServerSync(schedule.serverId) ?: run {
                _snapshots.value = UiState.Error("Server not found")
                return@launch
            }

            serverType = server!!.serverType
            isSnapshotMode = schedule.snapshotRetention > 0

            if (snapshotNameArg == Constants.FLAT_BROWSE_SENTINEL || (!isSnapshotMode && snapshotNameArg != null)) {
                browseFlat("")
            } else if (snapshotNameArg != null) {
                browseSnapshot(snapshotNameArg, "")
            } else {
                loadSnapshots()
            }
        }
    }

    fun loadSnapshots() {
        val srv = server ?: return
        viewModelScope.launch {
            _snapshots.value = UiState.Loading
            try {
                val list = restoreService.listSnapshots(srv, destPath)
                _snapshots.value = UiState.Success(list)
            } catch (e: Exception) {
                _snapshots.value = UiState.Error(e.message ?: "Failed to list snapshots")
            }
        }
    }

    fun deleteSnapshot(snapshotName: String) {
        val srv = server ?: return
        viewModelScope.launch {
            try {
                restoreService.deleteSnapshot(srv, destPath, snapshotName)
                loadSnapshots()
            } catch (e: Exception) {
                _snapshots.value = UiState.Error(e.message ?: "Failed to delete snapshot")
            }
        }
    }

    fun browseSnapshot(snapshotName: String, relativePath: String) {
        val srv = server ?: return
        viewModelScope.launch {
            _files.value = UiState.Loading
            _currentPath.value = relativePath
            try {
                val entries = restoreService.browse(srv, destPath, snapshotName, relativePath)
                _files.value = UiState.Success(entries)
            } catch (e: Exception) {
                _files.value = UiState.Error(e.message ?: "Failed to browse")
            }
        }
    }

    fun browseFlat(relativePath: String) {
        val srv = server ?: return
        viewModelScope.launch {
            _files.value = UiState.Loading
            _currentPath.value = relativePath
            try {
                val entries = restoreService.browse(srv, destPath, null, relativePath)
                if (relativePath.isNotBlank()) {
                    flatNavigationStack.add(relativePath)
                }
                _files.value = UiState.Success(entries)
            } catch (e: Exception) {
                _files.value = UiState.Error(e.message ?: "Failed to browse")
            }
        }
    }

    fun navigateUp(snapshotName: String) {
        val current = _currentPath.value
        val parent = current.substringBeforeLast('/', "")
        browseSnapshot(snapshotName, parent)
    }

    fun navigateUpFlat() {
        if (flatNavigationStack.isNotEmpty()) {
            flatNavigationStack.removeAt(flatNavigationStack.lastIndex)
        }
        val parent = _currentPath.value.substringBeforeLast('/', "")
        browseFlat(parent)
    }

    fun restoreFile(snapshotName: String?, remotePath: String, localPath: String) {
        val srv = server ?: return
        viewModelScope.launch {
            _restoreState.value = RestoreProgressState(isRunning = true)
            try {
                val result = restoreService.restore(
                    server = srv,
                    destPath = destPath,
                    snapshotName = snapshotName,
                    remotePath = remotePath,
                    localPath = localPath,
                    onProgress = { output ->
                        val current = _restoreState.value
                        when (output) {
                            is RsyncOutput.Progress -> {
                                _restoreState.value = current.copy(
                                    currentFile = output.fileName,
                                    percentage = output.percentage
                                )
                            }
                            is RsyncOutput.Log -> {
                                _restoreState.value = current.copy(
                                    logOutput = current.logOutput + output.line + "\n"
                                )
                            }
                            else -> {}
                        }
                    }
                )
                _restoreState.value = _restoreState.value.copy(
                    isRunning = false,
                    isCompleted = true,
                    success = result.success,
                    errorMessage = result.errorMessage,
                    filesRestored = result.filesRestored
                )
            } catch (e: Exception) {
                _restoreState.value = _restoreState.value.copy(
                    isRunning = false,
                    isCompleted = true,
                    success = false,
                    errorMessage = e.message
                )
            }
        }
    }
}

data class RestoreProgressState(
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val success: Boolean = false,
    val currentFile: String = "",
    val percentage: Int = 0,
    val logOutput: String = "",
    val errorMessage: String? = null,
    val filesRestored: Int = 0
)
