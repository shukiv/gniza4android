package com.gniza.backup.ui.screens.restore

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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gniza.backup.domain.model.Snapshot
import com.gniza.backup.ui.components.EmptyState
import com.gniza.backup.ui.components.GnizaTopAppBar
import com.gniza.backup.ui.components.LoadingIndicator
import com.gniza.backup.ui.navigation.Screen
import com.gniza.backup.ui.util.UiState
import android.net.Uri

@Composable
fun RestoreSnapshotListScreen(
    navController: NavController,
    viewModel: RestoreViewModel = hiltViewModel()
) {
    val snapshotsState by viewModel.snapshots.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "Snapshots",
                onNavigateBack = { navController.popBackStack() }
            )
        }
    ) { innerPadding ->
        when (val state = snapshotsState) {
            is UiState.Loading -> {
                LoadingIndicator(modifier = Modifier.padding(innerPadding))
            }
            is UiState.Error -> {
                EmptyState(
                    icon = Icons.Default.History,
                    message = state.message,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.History,
                        message = "No snapshots found on the server.",
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
                        items(state.data) { snapshot ->
                            SnapshotCard(
                                snapshot = snapshot,
                                onClick = {
                                    navController.navigate(
                                        Screen.RestoreSnapshotBrowse.route
                                            .replace("{scheduleId}", viewModel.scheduleId.toString())
                                            .replace("{snapshotName}", Uri.encode(snapshot.name))
                                    )
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

@Composable
private fun SnapshotCard(
    snapshot: Snapshot,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = if (snapshot.isLatest) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.name,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (snapshot.isLatest) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Latest",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Latest",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
