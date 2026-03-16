package com.gniza.backup.ui.screens.restore

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gniza.backup.domain.model.RemoteFileEntry
import com.gniza.backup.ui.components.EmptyState
import com.gniza.backup.ui.components.GnizaTopAppBar
import com.gniza.backup.ui.components.LoadingIndicator
import com.gniza.backup.ui.util.UiState
import com.gniza.backup.util.FileUtils

@Composable
fun RestoreSnapshotBrowseScreen(
    navController: NavController,
    snapshotName: String,
    viewModel: RestoreViewModel = hiltViewModel()
) {
    val filesState by viewModel.files.collectAsStateWithLifecycle()
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val restoreState by viewModel.restoreState.collectAsStateWithLifecycle()

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePath by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = snapshotName,
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(
                        onClick = {
                            restorePath = ""
                            showRestoreDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore all"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Breadcrumb / path indicator
            if (currentPath.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.navigateUp(snapshotName) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go up"
                        )
                    }
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Restore progress
            if (restoreState.isRunning) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Restoring: ${restoreState.currentFile}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { restoreState.percentage / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${restoreState.percentage}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (restoreState.isCompleted) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (restoreState.success) "Restore Completed" else "Restore Failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (restoreState.success) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        if (restoreState.success) {
                            Text(
                                text = "${restoreState.filesRestored} files restored",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        restoreState.errorMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // File list
            when (val state = filesState) {
                is UiState.Loading -> {
                    LoadingIndicator()
                }
                is UiState.Error -> {
                    EmptyState(
                        icon = Icons.Default.History,
                        message = state.message
                    )
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Folder,
                            message = "Empty directory"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(state.data) { entry ->
                                FileEntryCard(
                                    entry = entry,
                                    onTap = {
                                        if (entry.isDirectory) {
                                            viewModel.browseSnapshot(snapshotName, entry.path)
                                        } else {
                                            restorePath = entry.path
                                            showRestoreDialog = true
                                        }
                                    },
                                    onRestore = {
                                        restorePath = entry.path
                                        showRestoreDialog = true
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showRestoreDialog) {
        val defaultLocalPath = Environment.getExternalStorageDirectory().absolutePath + "/GnizaRestore/"
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore") },
            text = {
                Column {
                    if (restorePath.isNotBlank()) {
                        Text("Restore \"$restorePath\" to:")
                    } else {
                        Text("Restore entire snapshot to:")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = defaultLocalPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRestoreDialog = false
                    viewModel.restoreFile(snapshotName, restorePath, defaultLocalPath)
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FileEntryCard(
    entry: RemoteFileEntry,
    onTap: () -> Unit,
    onRestore: () -> Unit
) {
    ElevatedCard(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (entry.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!entry.isDirectory) {
                    Text(
                        text = FileUtils.formatBytes(entry.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
