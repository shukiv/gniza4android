package com.gniza.backup.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
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
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.ScheduleInterval
import com.gniza.backup.ui.components.GnizaTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    navController: NavController,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: SetupWizardViewModel = hiltViewModel()
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    val qrResult = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("qr_result", null)
        ?.collectAsState()

    LaunchedEffect(qrResult?.value) {
        qrResult?.value?.let { json ->
            viewModel.applyQrData(json)
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("qr_result")
        }
    }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "Setup",
                actions = {
                    if (currentStep < 4) {
                        TextButton(onClick = onSkip) {
                            Text(text = "Skip")
                        }
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
            LinearProgressIndicator(
                progress = { currentStep / 4f },
                modifier = Modifier.fillMaxWidth()
            )

            when (currentStep) {
                0 -> WelcomeStep(
                    onNext = { viewModel.nextStep() }
                )
                1 -> ServerStep(
                    viewModel = viewModel,
                    isSaving = isSaving,
                    onBack = { viewModel.previousStep() },
                    onNext = { viewModel.nextStep() },
                    onScanQr = { navController.navigate("qrscanner") }
                )
                2 -> SourceStep(
                    viewModel = viewModel,
                    isSaving = isSaving,
                    onBack = { viewModel.previousStep() },
                    onNext = { viewModel.nextStep() }
                )
                3 -> ScheduleStep(
                    viewModel = viewModel,
                    isSaving = isSaving,
                    onBack = { viewModel.previousStep() },
                    onFinish = { viewModel.nextStep() }
                )
                4 -> CompleteStep(
                    onGoToSchedules = onComplete
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Gniza Backup",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "This wizard will help you set up your first backup in 3 simple steps:\n\n" +
                    "1. Add a remote server - where your backups will be stored\n" +
                    "2. Create a backup source - choose which folders to back up\n" +
                    "3. Set up a schedule - decide when backups should run\n\n" +
                    "Let's get started!",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Get Started")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerStep(
    viewModel: SetupWizardViewModel,
    isSaving: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onScanQr: () -> Unit
) {
    val serverName by viewModel.serverName.collectAsStateWithLifecycle()
    val serverHost by viewModel.serverHost.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val serverUsername by viewModel.serverUsername.collectAsStateWithLifecycle()
    val serverAuthMethod by viewModel.serverAuthMethod.collectAsStateWithLifecycle()
    val serverPassword by viewModel.serverPassword.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Step 1: Add a Server",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter the connection details for the remote server where your backups will be stored. This is typically a Linux server or NAS that you can access via SSH.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onScanQr,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text = "Scan QR Code")
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = serverName,
            onValueChange = { viewModel.updateServerName(it) },
            label = { Text(text = "Server Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = serverHost,
            onValueChange = { viewModel.updateServerHost(it) },
            label = { Text(text = "Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = serverPort.toString(),
            onValueChange = { input ->
                input.toIntOrNull()?.let { viewModel.updateServerPort(it) }
            },
            label = { Text(text = "Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = serverUsername,
            onValueChange = { viewModel.updateServerUsername(it) },
            label = { Text(text = "Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Authentication Method",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        val authOptions = AuthMethod.entries
        val authLabels = listOf("Password", "SSH Key")

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            authOptions.forEachIndexed { index, method ->
                SegmentedButton(
                    selected = serverAuthMethod == method,
                    onClick = { viewModel.updateServerAuthMethod(method) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = authOptions.size
                    )
                ) {
                    Text(text = authLabels[index])
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (serverAuthMethod == AuthMethod.PASSWORD) {
            OutlinedTextField(
                value = serverPassword,
                onValueChange = { viewModel.updateServerPassword(it) },
                label = { Text(text = "Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            val sshKeyGenerated by viewModel.sshKeyGenerated.collectAsStateWithLifecycle()
            val sshPublicKey by viewModel.sshPublicKey.collectAsStateWithLifecycle()
            val clipboardManager = LocalClipboardManager.current

            if (!sshKeyGenerated) {
                Button(
                    onClick = { viewModel.generateSshKey() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text = "Generate SSH Key")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "An SSH key pair will be generated. You'll need to copy the public key to your server's ~/.ssh/authorized_keys file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "SSH key generated! Copy the public key below and add it to your server's ~/.ssh/authorized_keys file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sshPublicKey,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(text = "Public Key") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(sshPublicKey))
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy public key"
                            )
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text(text = "Back")
            }
            Button(
                onClick = onNext,
                enabled = !isSaving && serverHost.isNotBlank() && serverUsername.isNotBlank()
            ) {
                Text(text = "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceStep(
    viewModel: SetupWizardViewModel,
    isSaving: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val sourceName by viewModel.sourceName.collectAsStateWithLifecycle()
    val sourceFolders by viewModel.sourceFolders.collectAsStateWithLifecycle()
    var newFolder by remember { mutableStateOf("") }
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistent permission
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            // Convert to a usable path
            val path = uri.path?.replace("/tree/primary:", "/storage/emulated/0/")
                ?: uri.toString()
            viewModel.updateSourceFolders(sourceFolders + path)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Step 2: Create a Backup Source",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose a name for this backup source and select the folders you want to back up from your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = sourceName,
            onValueChange = { viewModel.updateSourceName(it) },
            label = { Text(text = "Source Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Folders to back up",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text = "Pick a Folder")
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (sourceFolders.isNotEmpty()) {
            sourceFolders.forEach { folder ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = {
                        Text(
                            text = folder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                viewModel.updateSourceFolders(sourceFolders - folder)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove folder"
                            )
                        }
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newFolder,
                onValueChange = { newFolder = it },
                label = { Text(text = "Or type a path") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (newFolder.isNotBlank()) {
                        viewModel.updateSourceFolders(sourceFolders + newFolder.trim())
                        newFolder = ""
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add folder"
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text(text = "Back")
            }
            Button(
                onClick = onNext,
                enabled = !isSaving && sourceName.isNotBlank() && sourceFolders.isNotEmpty()
            ) {
                Text(text = "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleStep(
    viewModel: SetupWizardViewModel,
    isSaving: Boolean,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val scheduleName by viewModel.scheduleName.collectAsStateWithLifecycle()
    val scheduleDestinationPath by viewModel.scheduleDestinationPath.collectAsStateWithLifecycle()
    val scheduleInterval by viewModel.scheduleInterval.collectAsStateWithLifecycle()

    val intervalOptions = listOf(ScheduleInterval.HOURLY, ScheduleInterval.DAILY, ScheduleInterval.WEEKLY)
    val intervalLabels = listOf("Hourly", "Daily", "Weekly")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Step 3: Set Up a Schedule",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Configure when this backup should run automatically. You can also run backups manually at any time from the Schedules tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = scheduleName,
            onValueChange = { viewModel.updateScheduleName(it) },
            label = { Text(text = "Schedule Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = scheduleDestinationPath,
            onValueChange = { viewModel.updateScheduleDestinationPath(it) },
            label = { Text(text = "Destination Path on Server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Backup Interval",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            intervalOptions.forEachIndexed { index, interval ->
                SegmentedButton(
                    selected = scheduleInterval == interval,
                    onClick = { viewModel.updateScheduleInterval(interval) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = intervalOptions.size
                    )
                ) {
                    Text(text = intervalLabels[index])
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text(text = "Back")
            }
            Button(
                onClick = onFinish,
                enabled = !isSaving && scheduleName.isNotBlank() && scheduleDestinationPath.isNotBlank()
            ) {
                Text(text = "Finish")
            }
        }
    }
}

@Composable
private fun CompleteStep(onGoToSchedules: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your backup is configured and ready to go. You can manage your servers, sources, and schedules from the main tabs.\n\n" +
                    "Tip: Tap 'Run Now' on any schedule to start a backup immediately.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGoToSchedules,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Go to Schedules")
        }
    }
}
