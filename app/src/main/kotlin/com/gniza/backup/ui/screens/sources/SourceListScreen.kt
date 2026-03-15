package com.gniza.backup.ui.screens.sources

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gniza.backup.domain.model.BackupSource
import com.gniza.backup.ui.components.ConfirmDialog
import com.gniza.backup.ui.components.EmptyState
import com.gniza.backup.ui.components.GnizaTopAppBar
import com.gniza.backup.ui.components.LoadingIndicator
import com.gniza.backup.ui.navigation.Screen
import com.gniza.backup.ui.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceListScreen(
    navController: NavController,
    viewModel: SourceViewModel = hiltViewModel()
) {
    val sourcesState by viewModel.sources.collectAsStateWithLifecycle()

    var sourceToDelete by remember { mutableStateOf<BackupSource?>(null) }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "Sources",
                actions = {
                    IconButton(onClick = { navController.navigate("help") }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(
                        Screen.SourceEdit.route.replace("{sourceId}", "0")
                    )
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add source"
                )
            }
        }
    ) { innerPadding ->
        when (val state = sourcesState) {
            is UiState.Loading -> {
                LoadingIndicator(modifier = Modifier.padding(innerPadding))
            }
            is UiState.Error -> {
                EmptyState(
                    icon = Icons.Default.FolderCopy,
                    message = state.message,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.FolderCopy,
                        message = "No sources yet. Tap + to create one.",
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(
                            items = state.data,
                            key = { it.id }
                        ) { source ->
                            SourceCard(
                                source = source,
                                onTap = {
                                    navController.navigate(
                                        Screen.SourceEdit.route.replace(
                                            "{sourceId}",
                                            source.id.toString()
                                        )
                                    )
                                },
                                onDelete = { sourceToDelete = source },
                                onEnabledChange = { enabled ->
                                    viewModel.updateEditSource(source.copy(enabled = enabled))
                                    viewModel.saveSource {}
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    sourceToDelete?.let { source ->
        ConfirmDialog(
            title = "Delete Source",
            message = "Are you sure you want to delete \"${source.name}\"? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteSource(source)
                sourceToDelete = null
            },
            onDismiss = { sourceToDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(
    source: BackupSource,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.Transparent
                },
                label = "swipe_color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        ElevatedCard(
            onClick = onTap,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = source.name.ifEmpty { "Unnamed Source" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete source",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = onEnabledChange
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${source.sourceFolders.size} folder(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (source.excludePatterns.isNotEmpty()) {
                        Text(
                            text = "${source.excludePatterns.size} exclude pattern(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
