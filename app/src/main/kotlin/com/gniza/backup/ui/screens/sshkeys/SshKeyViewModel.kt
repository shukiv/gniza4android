package com.gniza.backup.ui.screens.sshkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.service.ssh.SshKeyInfo
import com.gniza.backup.service.ssh.SshKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenerateKeyDialogState(
    val isVisible: Boolean = false,
    val name: String = "",
    val type: String = "RSA"
)

@HiltViewModel
class SshKeyViewModel @Inject constructor(
    private val sshKeyManager: SshKeyManager
) : ViewModel() {

    private val _keys = MutableStateFlow<List<SshKeyInfo>>(emptyList())
    val keys: StateFlow<List<SshKeyInfo>> = _keys.asStateFlow()

    private val _selectedKeyPublicContent = MutableStateFlow<String?>(null)
    val selectedKeyPublicContent: StateFlow<String?> = _selectedKeyPublicContent.asStateFlow()

    private val _generateDialogState = MutableStateFlow(GenerateKeyDialogState())
    val generateDialogState: StateFlow<GenerateKeyDialogState> = _generateDialogState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        loadKeys()
    }

    fun loadKeys() {
        viewModelScope.launch {
            _keys.value = sshKeyManager.listKeys()
        }
    }

    fun loadPublicKey(name: String) {
        viewModelScope.launch {
            _selectedKeyPublicContent.value = sshKeyManager.getPublicKey(name)
        }
    }

    fun clearPublicKey() {
        _selectedKeyPublicContent.value = null
    }

    fun generateKey(name: String, type: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            sshKeyManager.generateKeyPair(name, type)
            _isGenerating.value = false
            _generateDialogState.value = GenerateKeyDialogState()
            loadKeys()
        }
    }

    fun deleteKey(name: String) {
        viewModelScope.launch {
            sshKeyManager.deleteKey(name)
            loadKeys()
        }
    }

    fun showGenerateDialog() {
        _generateDialogState.value = GenerateKeyDialogState(isVisible = true)
    }

    fun dismissGenerateDialog() {
        _generateDialogState.value = GenerateKeyDialogState()
    }

    fun updateGenerateDialogName(name: String) {
        _generateDialogState.value = _generateDialogState.value.copy(name = name)
    }

    fun updateGenerateDialogType(type: String) {
        _generateDialogState.value = _generateDialogState.value.copy(type = type)
    }
}
