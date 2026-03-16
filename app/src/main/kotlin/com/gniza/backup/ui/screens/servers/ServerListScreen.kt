package com.gniza.backup.ui.screens.servers

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.Server
import com.gniza.backup.ui.components.ConfirmDialog
import com.gniza.backup.ui.components.EmptyState
import com.gniza.backup.ui.components.GnizaTopAppBar
import com.gniza.backup.ui.components.LoadingIndicator
import com.gniza.backup.ui.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    navController: NavController,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val serversState by viewModel.servers.collectAsState()
    var serverToDelete by remember { mutableStateOf<Server?>(null) }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "Servers",
                actions = {
                    IconButton(onClick = { navController.navigate("qrscanner") }) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Scan QR Code"
                        )
                    }
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
                onClick = { navController.navigate("servers/0/edit") }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add server"
                )
            }
        }
    ) { innerPadding ->
        when (val state = serversState) {
            is UiState.Loading -> {
                LoadingIndicator(modifier = Modifier.padding(innerPadding))
            }

            is UiState.Error -> {
                EmptyState(
                    icon = Icons.Default.Dns,
                    message = state.message,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Dns,
                        message = "No servers configured",
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
                        items(
                            items = state.data,
                            key = { it.id }
                        ) { server ->
                            ServerListItem(
                                server = server,
                                onTap = {
                                    navController.navigate("servers/${server.id}/edit")
                                },
                                onTestClick = {
                                    viewModel.updateEditServer(server)
                                    viewModel.testConnection()
                                },
                                onSwipeToDelete = {
                                    serverToDelete = server
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    serverToDelete?.let { server ->
        ConfirmDialog(
            title = "Delete Server",
            message = "Are you sure you want to delete \"${server.name}\"?",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteServer(server)
                serverToDelete = null
            },
            onDismiss = { serverToDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerListItem(
    server: Server,
    onTap: () -> Unit,
    onTestClick: () -> Unit,
    onSwipeToDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeToDelete()
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
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        ElevatedCard(
            onClick = onTap,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (server.authMethod == AuthMethod.PASSWORD) {
                        Icons.Default.Lock
                    } else {
                        Icons.Default.Key
                    },
                    contentDescription = if (server.authMethod == AuthMethod.PASSWORD) {
                        "Password authentication"
                    } else {
                        "SSH key authentication"
                    },
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name.ifEmpty { server.host },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${server.host}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onTestClick) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Test connection"
                    )
                }
                IconButton(onClick = onSwipeToDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete server",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
