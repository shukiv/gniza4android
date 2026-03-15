package com.gniza.backup.ui.screens.sources

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gniza.backup.ui.components.GnizaTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SourceEditScreen(
    navController: NavController,
    sourceId: Long,
    viewModel: SourceViewModel = hiltViewModel()
) {
    val source by viewModel.editSource.collectAsStateWithLifecycle()

    val isNew = sourceId == 0L

    LaunchedEffect(sourceId) {
        viewModel.loadSource(sourceId)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Convert content URI to real filesystem path
            val path = uriToFilePath(it)
            if (path != null) {
                viewModel.addSourceFolder(path)
            }
        }
    }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = if (isNew) "Add Source" else "Edit Source",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveSource {
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
                value = source.name,
                onValueChange = { viewModel.updateEditSource(source.copy(name = it)) },
                label = { Text("Source Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Source Folders
            HorizontalDivider()
            Text(
                text = "Source Folders",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                source.sourceFolders.forEachIndexed { index, folder ->
                    val displayPath = Uri.parse(folder).lastPathSegment ?: folder
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(displayPath) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove folder",
                                modifier = Modifier.clickable {
                                    viewModel.removeSourceFolder(index)
                                }
                            )
                        },
                        colors = InputChipDefaults.inputChipColors()
                    )
                }
                AssistChip(
                    onClick = { folderPickerLauncher.launch(null) },
                    label = { Text("Add Folder") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    }
                )
            }

            // Exclude patterns
            OutlinedTextField(
                value = source.excludePatterns.joinToString(", "),
                onValueChange = { text ->
                    val patterns = text.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    viewModel.updateEditSource(source.copy(excludePatterns = patterns))
                },
                label = { Text("Exclude Patterns") },
                placeholder = { Text("*.tmp, .cache, *.log") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Include patterns
            OutlinedTextField(
                value = source.includePatterns.joinToString(", "),
                onValueChange = { text ->
                    val patterns = text.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    viewModel.updateEditSource(source.copy(includePatterns = patterns))
                },
                label = { Text("Include Patterns") },
                placeholder = { Text("*.jpg, *.pdf, Documents/**") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun uriToFilePath(uri: Uri): String? {
    // Handle SAF tree URIs like content://com.android.externalstorage.documents/tree/primary%3ADCIM
    val docId = try {
        val path = uri.path ?: return null
        // Tree URIs have format: /tree/primary:path/to/folder
        val treePart = path.substringAfter("/tree/", "")
        if (treePart.isEmpty()) return null
        java.net.URLDecoder.decode(treePart, "UTF-8")
    } catch (_: Exception) {
        return null
    }

    // docId is like "primary:DCIM" or "primary:Download/subfolder"
    val parts = docId.split(":", limit = 2)
    if (parts.size != 2) return null

    val storageType = parts[0]
    val relativePath = parts[1]

    return when (storageType) {
        "primary" -> "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        else -> "/storage/$storageType/$relativePath"
    }
}
