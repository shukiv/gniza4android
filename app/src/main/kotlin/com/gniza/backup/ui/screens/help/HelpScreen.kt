package com.gniza.backup.ui.screens.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gniza.backup.ui.components.GnizaTopAppBar

@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "Help",
                onNavigateBack = onNavigateBack
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
            HelpSectionHeader(text = "Getting Started")
            Text(
                text = "Gniza Backup helps you automatically back up folders from your Android device to a remote server using rsync (for efficient incremental backups) or SFTP as a fallback.\n\n" +
                        "To set up a backup, you need three things:\n" +
                        "1. A Server - the remote machine where backups are stored\n" +
                        "2. A Source - the folders on your device to back up\n" +
                        "3. A Schedule - when and how often to run the backup",
                style = MaterialTheme.typography.bodyMedium
            )

            HelpSpacer()

            HelpSectionHeader(text = "Servers")
            Text(
                text = "Servers are the remote machines where your backups are stored. Typically, this is a Linux server, NAS, or any machine accessible via SSH.\n\n" +
                        "To add a server, go to the Servers tab and tap the + button. You'll need:\n" +
                        "- Hostname or IP address\n" +
                        "- SSH port (usually 22)\n" +
                        "- Username\n" +
                        "- Authentication method (password or SSH key)\n\n" +
                        "Tip: SSH key authentication is more secure and doesn't require entering a password each time.",
                style = MaterialTheme.typography.bodyMedium
            )

            HelpSpacer()

            HelpSectionHeader(text = "Sources")
            Text(
                text = "Sources define what to back up from your device. Each source has a name and a list of folders to include.\n\n" +
                        "You can also specify include and exclude patterns to fine-tune which files are backed up. For example, exclude '*.tmp' to skip temporary files.\n\n" +
                        "Sources are independent of servers and schedules - you can back up the same source to multiple servers by creating multiple schedules.",
                style = MaterialTheme.typography.bodyMedium
            )

            HelpSpacer()

            HelpSectionHeader(text = "Schedules")
            Text(
                text = "Schedules tie everything together. A schedule connects a source (what to back up) with a server (where to send it) and defines when backups should run.\n\n" +
                        "Available intervals:\n" +
                        "- Manual - only runs when you tap 'Run Now'\n" +
                        "- Hourly - runs every hour\n" +
                        "- Daily - runs once a day\n" +
                        "- Weekly - runs once a week\n\n" +
                        "You can also set constraints like 'Wi-Fi only' to avoid using mobile data, or 'While charging' to preserve battery.",
                style = MaterialTheme.typography.bodyMedium
            )

            HelpSpacer()

            HelpSectionHeader(text = "SSH Keys")
            Text(
                text = "SSH keys provide a more secure way to authenticate with your server without entering a password.\n\n" +
                        "To use SSH keys:\n" +
                        "1. Go to Settings > Manage SSH Keys\n" +
                        "2. Generate a new key pair\n" +
                        "3. Copy the public key to your server's ~/.ssh/authorized_keys file\n" +
                        "4. When adding a server, select 'SSH Key' as the authentication method",
                style = MaterialTheme.typography.bodyMedium
            )

            HelpSpacer()

            HelpSectionHeader(text = "Rsync & SFTP")
            Text(
                text = "Gniza Backup uses rsync for efficient incremental backups - only changed files are transferred after the first backup. The app bundles rsync and SSH (Dropbear) binaries, so no additional software is needed on your device.\n\n" +
                        "If rsync is not available on the remote server, the app falls back to SFTP, which transfers all files each time.\n\n" +
                        "You can check the status of bundled binaries in Settings.",
                style = MaterialTheme.typography.bodyMedium
            )

            HelpSpacer()

            HelpSectionHeader(text = "Logs")
            Text(
                text = "Every backup run is logged with details including:\n" +
                        "- Start and completion time\n" +
                        "- Number of files transferred\n" +
                        "- Total bytes transferred\n" +
                        "- Full rsync output\n" +
                        "- Any error messages\n\n" +
                        "Tap on any log entry to see the full details. Use the Copy button to share error messages for troubleshooting.",
                style = MaterialTheme.typography.bodyMedium
            )

            HelpSpacer()

            HelpSectionHeader(text = "Troubleshooting")
            Text(
                text = "Common issues:\n\n" +
                        "- 'Connection refused': Check that the server hostname/IP and port are correct, and that SSH is running on the server.\n\n" +
                        "- 'Authentication failed': Verify your username and password/SSH key. Make sure the public key is in the server's authorized_keys file.\n\n" +
                        "- 'Permission denied': The remote user may not have write access to the destination path. Check folder permissions on the server.\n\n" +
                        "- 'SFTP fallback': If you see this, rsync is not installed on the remote server. Install rsync on the server for faster incremental backups.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HelpSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun HelpSpacer() {
    Spacer(modifier = Modifier.height(16.dp))
}
