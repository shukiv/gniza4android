package com.gniza.backup.ui.screens.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.repository.BackupSourceRepository
import com.gniza.backup.domain.model.BackupSource
import com.gniza.backup.ui.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SourceViewModel @Inject constructor(
    private val backupSourceRepository: BackupSourceRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sources: StateFlow<UiState<List<BackupSource>>> = backupSourceRepository.allSources
        .map<List<BackupSource>, UiState<List<BackupSource>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load sources")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    private val _editSource = MutableStateFlow(BackupSource())
    val editSource: StateFlow<BackupSource> = _editSource.asStateFlow()

    fun loadSource(id: Long) {
        if (id == 0L) {
            _editSource.value = BackupSource()
            return
        }
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                backupSourceRepository.getSourceSync(id)
            }
            source?.let { _editSource.value = it }
        }
    }

    fun updateEditSource(source: BackupSource) {
        _editSource.value = source
    }

    fun saveSource(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val source = _editSource.value.copy(
                updatedAt = System.currentTimeMillis()
            )
            backupSourceRepository.saveSource(source)
            onSuccess()
        }
    }

    fun deleteSource(source: BackupSource) {
        viewModelScope.launch {
            backupSourceRepository.deleteSource(source)
        }
    }

    fun addSourceFolder(path: String) {
        val current = _editSource.value
        _editSource.value = current.copy(
            sourceFolders = current.sourceFolders + path
        )
    }

    fun removeSourceFolder(index: Int) {
        val current = _editSource.value
        _editSource.value = current.copy(
            sourceFolders = current.sourceFolders.toMutableList().apply { removeAt(index) }
        )
    }
}
