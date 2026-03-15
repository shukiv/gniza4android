package com.gniza.backup.ui.screens.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.repository.BackupLogRepository
import com.gniza.backup.domain.model.BackupLog
import com.gniza.backup.ui.components.GnizaTopAppBar
import com.gniza.backup.ui.components.LoadingIndicator
import com.gniza.backup.ui.components.StatusBadge
import com.gniza.backup.ui.util.UiState
import com.gniza.backup.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogDetailViewModel @Inject constructor(
    backupLogRepository: BackupLogRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val logId: Long = savedStateHandle["logId"] ?: 0L

    val log: StateFlow<UiState<BackupLog>> = backupLogRepository.getLog(logId)
        .map<BackupLog?, UiState<BackupLog>> { log ->
            if (log != null) UiState.Success(log) else UiState.Error("Log not found")
        }
        .catch { emit(UiState.Error(it.message ?: "Failed to load log")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)
}

@Composable
fun LogDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: LogDetailViewModel = hiltViewModel()
) {
    val logState by viewModel.log.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "Log Details",
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        when (val state = logState) {
            is UiState.Loading -> {
                LoadingIndicator(modifier = Modifier.padding(innerPadding))
            }

            is UiState.Error -> {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(innerPadding).padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }

            is UiState.Success -> {
                LogDetailContent(
                    log = state.data,
                    context = context,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun LogDetailContent(
    log: BackupLog,
    context: Context,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row {
            Text(
                text = log.sourceName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            StatusBadge(status = log.status)
        }

        Text(
            text = log.serverName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                DetailRow("Started", dateFormat.format(Date(log.startedAt)))
                log.completedAt?.let {
                    DetailRow("Completed", dateFormat.format(Date(it)))
                }
                log.durationSeconds?.let {
                    DetailRow("Duration", FileUtils.formatDuration(it))
                }
                log.filesTransferred?.let {
                    DetailRow("Files transferred", it.toString())
                }
                log.bytesTransferred?.let {
                    DetailRow("Bytes transferred", FileUtils.formatBytes(it))
                }
                log.totalFiles?.let {
                    DetailRow("Total files", it.toString())
                }
            }
        }

        if (log.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Error", log.errorMessage)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy error"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Copy")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = log.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (!log.rsyncOutput.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Rsync Output",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Rsync Output", log.rsyncOutput)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Output copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy output"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Copy")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = log.rsyncOutput,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
