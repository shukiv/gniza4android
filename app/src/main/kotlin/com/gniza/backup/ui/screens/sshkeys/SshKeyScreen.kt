package com.gniza.backup.ui.screens.sshkeys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gniza.backup.service.ssh.SshKeyInfo
import com.gniza.backup.ui.components.ConfirmDialog
import com.gniza.backup.ui.components.EmptyState
import com.gniza.backup.ui.components.GnizaTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshKeyScreen(
    onNavigateBack: () -> Unit,
    viewModel: SshKeyViewModel = hiltViewModel()
) {
    val keys by viewModel.keys.collectAsState()
    val generateDialogState by viewModel.generateDialogState.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val selectedPublicKey by viewModel.selectedKeyPublicContent.collectAsState()
    var keyToDelete by remember { mutableStateOf<SshKeyInfo?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "SSH Keys",
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showGenerateDialog() }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Generate new key"
                )
            }
        }
    ) { innerPadding ->
        if (keys.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Key,
                message = "No SSH keys generated",
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
                    items = keys,
                    key = { it.name }
                ) { key ->
                    SshKeyItem(
                        keyInfo = key,
                        expandedPublicKey = if (selectedPublicKey != null && key.name == keys.find {
                            viewModel.selectedKeyPublicContent.value != null
                        }?.name) selectedPublicKey else null,
                        onTap = {
                            if (selectedPublicKey != null) {
                                viewModel.clearPublicKey()
                            } else {
                                viewModel.loadPublicKey(key.name)
                            }
                        },
                        onCopy = { publicKey ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SSH Public Key", publicKey)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Public key copied", Toast.LENGTH_SHORT).show()
                        },
                        onShare = { publicKey ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, publicKey)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Public Key"))
                        },
                        onSwipeToDelete = { keyToDelete = key }
                    )
                }
            }
        }
    }

    if (generateDialogState.isVisible) {
        GenerateKeyDialog(
            state = generateDialogState,
            isGenerating = isGenerating,
            onNameChange = { viewModel.updateGenerateDialogName(it) },
            onTypeChange = { viewModel.updateGenerateDialogType(it) },
            onConfirm = {
                viewModel.generateKey(
                    name = generateDialogState.name,
                    type = generateDialogState.type
                )
            },
            onDismiss = { viewModel.dismissGenerateDialog() }
        )
    }

    keyToDelete?.let { key ->
        ConfirmDialog(
            title = "Delete SSH Key",
            message = "Are you sure you want to delete \"${key.name}\"? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteKey(key.name)
                keyToDelete = null
            },
            onDismiss = { keyToDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SshKeyItem(
    keyInfo: SshKeyInfo,
    expandedPublicKey: String?,
    onTap: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = keyInfo.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = keyInfo.type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = keyInfo.fingerprint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (expandedPublicKey != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Public Key",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = expandedPublicKey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { onCopy(expandedPublicKey) }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy public key"
                            )
                        }
                        IconButton(onClick = { onShare(expandedPublicKey) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share public key"
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateKeyDialog(
    state: GenerateKeyDialogState,
    isGenerating: Boolean,
    onNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val keyTypes = listOf("RSA", "Ed25519")

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = { Text(text = "Generate SSH Key") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(text = "Key Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Key Type",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    keyTypes.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = state.type == type,
                            onClick = { onTypeChange(type) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = keyTypes.size
                            ),
                            enabled = !isGenerating
                        ) {
                            Text(text = type)
                        }
                    }
                }
                if (isGenerating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = "Generating key...")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.name.isNotBlank() && !isGenerating
            ) {
                Text(text = "Generate")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isGenerating
            ) {
                Text(text = "Cancel")
            }
        }
    )
}
