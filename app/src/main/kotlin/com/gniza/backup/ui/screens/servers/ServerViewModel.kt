package com.gniza.backup.ui.screens.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.repository.ServerRepository
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.Server
import com.gniza.backup.service.ssh.SshConnectionTest
import com.gniza.backup.service.ssh.SshConnectionTest.ConnectionTestResult
import com.gniza.backup.service.ssh.SshKeyInfo
import com.gniza.backup.service.ssh.SshKeyManager
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
class ServerViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val sshConnectionTest: SshConnectionTest,
    private val sshKeyManager: SshKeyManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val servers: StateFlow<UiState<List<Server>>> = serverRepository.allServers
        .map<List<Server>, UiState<List<Server>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load servers")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    private val _editServer = MutableStateFlow(Server())
    val editServer: StateFlow<Server> = _editServer.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    private val _availableKeys = MutableStateFlow<List<SshKeyInfo>>(emptyList())
    val availableKeys: StateFlow<List<SshKeyInfo>> = _availableKeys.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    init {
        loadAvailableKeys()
    }

    fun loadServer(id: Long) {
        if (id == 0L) {
            _editServer.value = Server()
            _connectionTestResult.value = null
            return
        }
        viewModelScope.launch {
            val server = withContext(Dispatchers.IO) {
                serverRepository.getServerSync(id)
            }
            server?.let { _editServer.value = it }
            _connectionTestResult.value = null
        }
    }

    fun updateEditServer(server: Server) {
        _editServer.value = server
    }

    fun saveServer(onSuccess: () -> Unit) {
        val server = _editServer.value

        val error = validateServer(server)
        if (error != null) {
            _validationError.value = error
            return
        }
        _validationError.value = null

        viewModelScope.launch {
            val serverToSave = server.copy(
                updatedAt = System.currentTimeMillis()
            )
            serverRepository.saveServer(serverToSave)
            onSuccess()
        }
    }

    private companion object {
        val HOST_REGEX = Regex("^[a-zA-Z0-9.:\\-]+$")
        val USERNAME_INVALID_CHARS = Regex("[@;|&\$`\\s]")
    }

    private fun validateServer(server: Server): String? {
        if (server.port !in 1..65535) {
            return "Port must be between 1 and 65535"
        }

        if (server.host.isBlank() || !HOST_REGEX.matches(server.host)) {
            return "Host contains invalid characters"
        }

        if (server.username.isBlank()) {
            return "Username must not be empty"
        }

        if (USERNAME_INVALID_CHARS.containsMatchIn(server.username)) {
            return "Username contains invalid characters"
        }

        return null
    }

    fun deleteServer(server: Server) {
        viewModelScope.launch {
            serverRepository.deleteServer(server)
        }
    }

    fun applyQrData(json: String) {
        viewModelScope.launch {
            try {
                val obj = org.json.JSONObject(json)
                if (!obj.has("gniza")) return@launch
                val server = Server(
                    name = obj.optString("host", "Server"),
                    host = obj.optString("host", ""),
                    port = obj.optInt("port", 22),
                    username = obj.optString("user", ""),
                    authMethod = if (obj.optString("auth") == "password") AuthMethod.PASSWORD else AuthMethod.SSH_KEY,
                    password = if (obj.has("pass")) obj.optString("pass", "") else null
                )
                serverRepository.saveServer(server)
            } catch (_: Exception) { }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTesting.value = true
            _connectionTestResult.value = null
            _connectionTestResult.value = sshConnectionTest.testConnection(_editServer.value)
            _isTesting.value = false
        }
    }

    private fun loadAvailableKeys() {
        viewModelScope.launch {
            _availableKeys.value = sshKeyManager.listKeys()
        }
    }
}
