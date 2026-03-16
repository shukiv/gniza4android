package com.gniza.backup.ui.screens.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.gniza.backup.domain.model.ScheduleInterval
import com.gniza.backup.ui.components.GnizaTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditScreen(
    navController: NavController,
    scheduleId: Long,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val schedule by viewModel.editSchedule.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    val isNew = scheduleId == 0L

    LaunchedEffect(scheduleId) {
        viewModel.loadSchedule(scheduleId)
    }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = if (isNew) "Add Schedule" else "Edit Schedule",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveSchedule {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Name
            OutlinedTextField(
                value = schedule.name,
                onValueChange = { viewModel.updateEditSchedule(schedule.copy(name = it)) },
                label = { Text("Schedule Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Source selection
            var sourceExpanded by remember { mutableStateOf(false) }
            val selectedSource = sources.find { it.id == schedule.sourceId }

            ExposedDropdownMenuBox(
                expanded = sourceExpanded,
                onExpandedChange = { sourceExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedSource?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Source") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = sourceExpanded,
                    onDismissRequest = { sourceExpanded = false }
                ) {
                    sources.forEach { source ->
                        DropdownMenuItem(
                            text = { Text(source.name.ifEmpty { "Unnamed Source" }) },
                            onClick = {
                                viewModel.updateEditSchedule(schedule.copy(sourceId = source.id))
                                sourceExpanded = false
                            }
                        )
                    }
                }
            }

            // Server selection
            var serverExpanded by remember { mutableStateOf(false) }
            val selectedServer = servers.find { it.id == schedule.serverId }

            ExposedDropdownMenuBox(
                expanded = serverExpanded,
                onExpandedChange = { serverExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedServer?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Server") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = serverExpanded,
                    onDismissRequest = { serverExpanded = false }
                ) {
                    servers.forEach { server ->
                        DropdownMenuItem(
                            text = { Text(server.name.ifEmpty { server.host }) },
                            onClick = {
                                viewModel.updateEditSchedule(schedule.copy(serverId = server.id))
                                serverExpanded = false
                            }
                        )
                    }
                }
            }

            // Destination path
            OutlinedTextField(
                value = schedule.destinationPath,
                onValueChange = {
                    viewModel.updateEditSchedule(schedule.copy(destinationPath = it))
                },
                label = { Text("Destination Path") },
                placeholder = { Text("/backup/device") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Interval selector
            HorizontalDivider()
            Text(
                text = "Interval",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScheduleInterval.entries.forEach { interval ->
                    FilterChip(
                        selected = schedule.interval == interval,
                        onClick = {
                            viewModel.updateEditSchedule(schedule.copy(interval = interval))
                        },
                        label = {
                            Text(
                                interval.name.lowercase().replaceFirstChar { it.uppercase() }
                            )
                        }
                    )
                }
            }

            // Constraints section
            HorizontalDivider()
            Text(
                text = "Constraints",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = schedule.wifiOnly,
                    onCheckedChange = {
                        viewModel.updateEditSchedule(schedule.copy(wifiOnly = it))
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wi-Fi only")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = schedule.whileCharging,
                    onCheckedChange = {
                        viewModel.updateEditSchedule(schedule.copy(whileCharging = it))
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("While charging")
            }

            // Snapshots section
            HorizontalDivider()
            Text(
                text = "Snapshots",
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                value = if (schedule.snapshotRetention > 0) schedule.snapshotRetention.toString() else "0",
                onValueChange = { value ->
                    val retention = value.filter { it.isDigit() }.toIntOrNull() ?: 0
                    viewModel.updateEditSchedule(schedule.copy(snapshotRetention = retention))
                },
                label = { Text("Snapshot Retention") },
                supportingText = { Text("Number of snapshots to keep (0 = flat backup, no snapshots)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Enabled toggle
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enabled")
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = {
                        viewModel.updateEditSchedule(schedule.copy(enabled = it))
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
