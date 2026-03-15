package com.gniza.backup.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gniza.backup.service.rsync.RsyncBinaryResolver
import com.gniza.backup.service.ssh.SshBinaryResolver
import com.gniza.backup.ui.components.GnizaTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val rsyncPath by viewModel.rsyncBinaryPath.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val logRetentionDays by viewModel.logRetentionDays.collectAsState()
    val darkThemeMode by viewModel.darkThemeMode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val rsyncResult by viewModel.rsyncResult.collectAsState()
    val isCheckingRsync by viewModel.isCheckingRsync.collectAsState()
    val sshResult by viewModel.sshResult.collectAsState()
    val isCheckingSsh by viewModel.isCheckingSsh.collectAsState()

    var rsyncPathInput by remember(rsyncPath) { mutableStateOf(rsyncPath) }
    var retentionInput by remember(logRetentionDays) { mutableStateOf(logRetentionDays.toString()) }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "Settings",
                actions = {
                    IconButton(onClick = { navController.navigate("help") }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help"
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Rsync Section
            SectionHeader(text = "Rsync")

            when (val result = rsyncResult) {
                is RsyncBinaryResolver.RsyncBinaryResult.Found -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Rsync found",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${result.path} (${result.source})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is RsyncBinaryResolver.RsyncBinaryResult.NotFound -> {
                    Text(
                        text = "Rsync not found. SFTP fallback will be used.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                null -> {
                    Text(
                        text = "Checking rsync...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = rsyncPathInput,
                onValueChange = { rsyncPathInput = it },
                label = { Text(text = "Custom Rsync Path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (rsyncPathInput != rsyncPath) {
                        IconButton(onClick = {
                            viewModel.setRsyncPath(rsyncPathInput)
                            viewModel.checkRsyncAvailability()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save path"
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.checkRsyncAvailability() },
                enabled = !isCheckingRsync
            ) {
                if (isCheckingRsync) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(text = "Check Availability")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SSH Client Section
            SectionHeader(text = "SSH Client")

            when (val result = sshResult) {
                is SshBinaryResolver.SshBinaryResult.Found -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (result.isDropbear) "Dropbear SSH found" else "OpenSSH found",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${result.path} (${result.source})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is SshBinaryResolver.SshBinaryResult.NotFound -> {
                    Text(
                        text = "SSH client not found. SFTP fallback will be used even if rsync is available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                null -> {
                    Text(
                        text = "Checking SSH...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.checkSshAvailability() },
                enabled = !isCheckingSsh
            ) {
                if (isCheckingSsh) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(text = "Check SSH")
            }

            SettingsDivider()

            // SSH Keys Section
            SectionHeader(text = "SSH Keys")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("sshkeys") }
                    .padding(vertical = 12.dp),
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
                        text = "Manage SSH Keys",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Generate, view, and delete SSH keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsDivider()

            // Default Constraints Section
            SectionHeader(text = "Default Constraints")

            SettingsSwitch(
                label = "Wi-Fi only",
                description = "Only run backups on unmetered networks",
                checked = wifiOnly,
                onCheckedChange = { viewModel.setWifiOnly(it) }
            )

            SettingsSwitch(
                label = "Notifications",
                description = "Show backup completion notifications",
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.setNotificationsEnabled(it) }
            )

            SettingsDivider()

            // Appearance Section
            SectionHeader(text = "Appearance")

            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            val themeOptions = listOf("system", "light", "dark")
            val themeLabels = listOf("System", "Light", "Dark")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeOptions.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = darkThemeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = themeOptions.size
                        )
                    ) {
                        Text(text = themeLabels[index])
                    }
                }
            }

            SettingsDivider()

            // Data Section
            SectionHeader(text = "Data")

            OutlinedTextField(
                value = retentionInput,
                onValueChange = { input ->
                    retentionInput = input
                    input.toIntOrNull()?.let { days ->
                        if (days > 0) viewModel.setLogRetention(days)
                    }
                },
                label = { Text(text = "Log Retention (days)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            SettingsDivider()

            // About Section
            SectionHeader(text = "About")

            Text(
                text = "Gniza Backup",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
