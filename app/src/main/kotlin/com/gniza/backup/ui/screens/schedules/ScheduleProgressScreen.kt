package com.gniza.backup.ui.screens.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.repository.BackupSourceRepository
import com.gniza.backup.data.repository.ScheduleRepository
import com.gniza.backup.service.backup.BackupExecutor
import com.gniza.backup.service.rsync.RsyncOutput
import com.gniza.backup.ui.components.GnizaTopAppBar
import com.gniza.backup.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleProgressState(
    val scheduleName: String = "",
    val sourceName: String = "",
    val currentFileName: String = "",
    val percentage: Int = 0,
    val speed: String = "",
    val logOutput: String = "",
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val result: BackupExecutor.BackupResult? = null
)

@HiltViewModel
class ScheduleProgressViewModel @Inject constructor(
    private val backupExecutor: BackupExecutor,
    private val scheduleRepository: ScheduleRepository,
    private val backupSourceRepository: BackupSourceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleProgressState())
    val state: StateFlow<ScheduleProgressState> = _state.asStateFlow()

    private var backupJob: Job? = null

    fun startBackup(scheduleId: Long) {
        if (_state.value.isRunning) return
        backupJob = viewModelScope.launch {
            val schedule = kotlinx.coroutines.withContext(Dispatchers.IO) {
                scheduleRepository.getScheduleSync(scheduleId)
            } ?: return@launch
            val source = kotlinx.coroutines.withContext(Dispatchers.IO) {
                backupSourceRepository.getSourceSync(schedule.sourceId)
            } ?: return@launch
            _state.value = ScheduleProgressState(
                scheduleName = schedule.name,
                sourceName = source.name,
                isRunning = true
            )
            val result = backupExecutor.execute(source, schedule) { output ->
                val current = _state.value
                when (output) {
                    is RsyncOutput.Progress -> {
                        _state.value = current.copy(
                            currentFileName = output.fileName,
                            percentage = output.percentage,
                            speed = output.speed
                        )
                    }
                    is RsyncOutput.FileComplete -> {
                        _state.value = current.copy(
                            logOutput = current.logOutput +
                                "Completed: ${output.fileName} (${output.size} bytes)\n"
                        )
                    }
                    is RsyncOutput.Summary -> {
                        _state.value = current.copy(
                            logOutput = current.logOutput +
                                "Summary: ${output.filesTransferred} files, " +
                                "${FileUtils.formatBytes(output.totalSize)}\n"
                        )
                    }
                    is RsyncOutput.Error -> {
                        _state.value = current.copy(
                            logOutput = current.logOutput + "ERROR: ${output.message}\n"
                        )
                    }
                    is RsyncOutput.Log -> {
                        _state.value = current.copy(
                            logOutput = current.logOutput + output.line + "\n"
                        )
                    }
                }
            }
            _state.value = _state.value.copy(
                isRunning = false,
                isCompleted = true,
                result = result,
                percentage = 100
            )
        }
    }

    fun cancelBackup() {
        backupJob?.cancel()
        _state.value = _state.value.copy(
            isRunning = false,
            isCompleted = true
        )
    }
}

@Composable
fun ScheduleProgressScreen(
    scheduleId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ScheduleProgressViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(scheduleId) {
        viewModel.startBackup(scheduleId)
    }

    LaunchedEffect(state.logOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = state.scheduleName.ifEmpty { "Backup Progress" },
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (state.isRunning) {
                Text(
                    text = state.currentFileName.ifEmpty { "Starting..." },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.percentage / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${state.percentage}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.speed.isNotEmpty()) {
                        Text(
                            text = state.speed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.isCompleted && state.result != null) {
                val result = state.result!!
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (result.success) "Backup Completed" else "Backup Failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (result.success) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Files transferred: ${result.filesTransferred}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Bytes transferred: ${FileUtils.formatBytes(result.bytesTransferred)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Duration: ${FileUtils.formatDuration(result.durationSeconds)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (result.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "Output",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = state.logOutput.ifEmpty { "Waiting for output..." },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (state.isRunning) {
                    OutlinedButton(onClick = { viewModel.cancelBackup() }) {
                        Text(text = "Cancel")
                    }
                } else {
                    Button(onClick = onNavigateBack) {
                        Text(text = "Done")
                    }
                }
            }
        }
    }
}
