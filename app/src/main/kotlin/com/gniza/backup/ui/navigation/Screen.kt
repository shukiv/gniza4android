package com.gniza.backup.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object ServerList : Screen("servers", "Servers", Icons.Default.Dns)
    object ServerEdit : Screen("servers/{serverId}/edit", "Server", Icons.Default.Dns)
    object SourceList : Screen("sources", "Sources", Icons.Default.FolderCopy)
    object SourceEdit : Screen("sources/{sourceId}/edit", "Source", Icons.Default.FolderCopy)
    object ScheduleList : Screen("schedules", "Schedules", Icons.Default.Schedule)
    object ScheduleEdit : Screen("schedules/{scheduleId}/edit", "Schedule", Icons.Default.Schedule)
    object ScheduleProgress : Screen("schedules/progress/{scheduleId}", "Running", Icons.Default.Schedule)
    object LogList : Screen("logs", "Logs", Icons.Default.History)
    object LogDetail : Screen("logs/{logId}", "Log", Icons.Default.History)
    object SshKeys : Screen("sshkeys", "SSH Keys", Icons.Default.Key)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object SetupWizard : Screen("setup", "Setup", Icons.Default.PlayArrow)
    object QrScanner : Screen("qrscanner", "Scan QR", Icons.Default.CameraAlt)
    object Help : Screen("help", "Help", Icons.AutoMirrored.Filled.HelpOutline)
}
