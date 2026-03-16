package com.gniza.backup.ui.screens.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.ui.components.GnizaTopAppBar
import com.gniza.backup.ui.theme.GnizaError
import com.gniza.backup.ui.theme.GnizaSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    navController: NavController,
    serverId: Long,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val editServer by viewModel.editServer.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val availableKeys by viewModel.availableKeys.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val qrDestinationPath by viewModel.qrDestinationPath.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var keyDropdownExpanded by remember { mutableStateOf(false) }

    val isNewServer = serverId == 0L

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val qrResult by savedStateHandle?.getLiveData<String>("qr_result")
        ?.observeAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(qrResult) {
        qrResult?.let { json ->
            viewModel.applyQrDataToEdit(json)
            savedStateHandle?.remove<String>("qr_result")
        }
    }

    LaunchedEffect(serverId) {
        viewModel.loadServer(serverId)
    }

    LaunchedEffect(connectionTestResult) {
        connectionTestResult?.let { result ->
            snackbarHostState.showSnackbar(result.message)
        }
    }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = if (isNewServer) "Add Server" else "Edit Server",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveServer {
                                navController.popBackStack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (isNewServer) {
                OutlinedButton(
                    onClick = { navController.navigate("qrscanner") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text = "Scan QR Code")
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            OutlinedTextField(
                value = editServer.name,
                onValueChange = { viewModel.updateEditServer(editServer.copy(name = it)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editServer.host,
                onValueChange = { viewModel.updateEditServer(editServer.copy(host = it)) },
                label = { Text("Host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editServer.port.toString(),
                onValueChange = { value ->
                    val port = value.filter { it.isDigit() }.toIntOrNull() ?: 0
                    viewModel.updateEditServer(editServer.copy(port = port))
                },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editServer.username,
                onValueChange = { viewModel.updateEditServer(editServer.copy(username = it)) },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Authentication Method",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = editServer.authMethod == AuthMethod.PASSWORD,
                    onClick = {
                        viewModel.updateEditServer(
                            editServer.copy(authMethod = AuthMethod.PASSWORD)
                        )
                    },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null
                        )
                    }
                )

                FilterChip(
                    selected = editServer.authMethod == AuthMethod.SSH_KEY,
                    onClick = {
                        viewModel.updateEditServer(
                            editServer.copy(authMethod = AuthMethod.SSH_KEY)
                        )
                    },
                    label = { Text("SSH Key") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null
                        )
                    }
                )
            }

            if (editServer.authMethod == AuthMethod.PASSWORD) {
                OutlinedTextField(
                    value = editServer.password ?: "",
                    onValueChange = { viewModel.updateEditServer(editServer.copy(password = it)) },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (editServer.authMethod == AuthMethod.SSH_KEY) {
                ExposedDropdownMenuBox(
                    expanded = keyDropdownExpanded,
                    onExpandedChange = { keyDropdownExpanded = it }
                ) {
                    val selectedKeyName = availableKeys
                        .find { it.privateKeyPath == editServer.privateKeyPath }
                        ?.let { "${it.name} (${it.type})" }
                        ?: ""

                    OutlinedTextField(
                        value = selectedKeyName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("SSH Key") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyDropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = keyDropdownExpanded,
                        onDismissRequest = { keyDropdownExpanded = false }
                    ) {
                        availableKeys.forEach { keyInfo ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "${keyInfo.name} (${keyInfo.type})",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = keyInfo.fingerprint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateEditServer(
                                        editServer.copy(privateKeyPath = keyInfo.privateKeyPath)
                                    )
                                    keyDropdownExpanded = false
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Generate New Key",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = {
                                keyDropdownExpanded = false
                                navController.navigate("sshkeys")
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = editServer.privateKeyPassphrase ?: "",
                    onValueChange = {
                        viewModel.updateEditServer(editServer.copy(privateKeyPassphrase = it))
                    },
                    label = { Text("Key Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (qrDestinationPath.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Remote backup folder",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = qrDestinationPath,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "This path will be available when creating a schedule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.testConnection() },
                enabled = !isTesting && editServer.host.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Test Connection")
                }
            }

            connectionTestResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) {
                            GnizaSuccess.copy(alpha = 0.1f)
                        } else {
                            GnizaError.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.success) GnizaSuccess else GnizaError
                        )
                        if (result.success) {
                            Text(
                                text = if (result.rsyncAvailable) {
                                    "rsync is available on server"
                                } else {
                                    "rsync not found - SFTP fallback will be used"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.saveServer {
                        navController.popBackStack()
                    }
                },
                enabled = editServer.host.isNotBlank() && editServer.username.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
