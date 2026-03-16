package com.gniza.backup.ui.screens.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.gniza.backup.data.repository.ServerRepository
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.Server
import com.gniza.backup.service.ssh.SshConnectionTest
import com.gniza.backup.service.ssh.SshConnectionTest.ConnectionTestResult
import com.gniza.backup.service.ssh.SshKeyInfo
import com.gniza.backup.service.ssh.SshKeyManager
import com.gniza.backup.ui.util.UiState
import com.gniza.backup.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
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
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
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

    private val _qrDestinationPath = MutableStateFlow("")
    val qrDestinationPath: StateFlow<String> = _qrDestinationPath.asStateFlow()

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

                var privateKeyPath: String? = null
                if (obj.has("key")) {
                    val compressed = android.util.Base64.decode(obj.getString("key"), android.util.Base64.DEFAULT)
                    val keyBytes = java.util.zip.GZIPInputStream(compressed.inputStream()).readBytes()
                    val keyName = "qr_${System.currentTimeMillis()}"
                    sshKeyManager.importKey(keyName, keyBytes)
                    privateKeyPath = sshKeyManager.getPrivateKeyPath(keyName)
                }

                if (obj.has("croc")) {
                    privateKeyPath = receiveCrocKey(obj.getString("croc"))
                }

                val server = Server(
                    name = obj.optString("name", obj.optString("host", "Server")),
                    host = obj.optString("host", ""),
                    port = obj.optInt("port", 22),
                    username = obj.optString("user", ""),
                    authMethod = if (obj.optString("auth") == "password") AuthMethod.PASSWORD else AuthMethod.SSH_KEY,
                    password = if (obj.has("pass")) obj.optString("pass", "") else null,
                    privateKeyPath = privateKeyPath
                )
                serverRepository.saveServer(server)
            } catch (_: Exception) { }
        }
    }

    fun applyQrDataToEdit(json: String) {
        viewModelScope.launch {
            try {
                val obj = org.json.JSONObject(json)
                if (!obj.has("gniza")) return@launch

                var privateKeyPath: String? = null
                if (obj.has("key")) {
                    val compressed = android.util.Base64.decode(obj.getString("key"), android.util.Base64.DEFAULT)
                    val keyBytes = java.util.zip.GZIPInputStream(compressed.inputStream()).readBytes()
                    val keyName = "qr_${System.currentTimeMillis()}"
                    sshKeyManager.importKey(keyName, keyBytes)
                    privateKeyPath = sshKeyManager.getPrivateKeyPath(keyName)
                }

                if (obj.has("croc")) {
                    privateKeyPath = receiveCrocKey(obj.getString("croc"))
                }

                // Refresh key list so the dropdown shows the imported key
                if (privateKeyPath != null) {
                    _availableKeys.value = sshKeyManager.listKeys()
                }

                _editServer.value = _editServer.value.copy(
                    name = obj.optString("name", obj.optString("host", "")),
                    host = obj.optString("host", ""),
                    port = obj.optInt("port", 22),
                    username = obj.optString("user", ""),
                    authMethod = if (obj.optString("auth") == "password") AuthMethod.PASSWORD else AuthMethod.SSH_KEY,
                    password = if (obj.has("pass")) obj.optString("pass", "") else null,
                    privateKeyPath = privateKeyPath
                )

                // Store the suggested destination path for use when creating schedules
                val path = obj.optString("path", "")
                if (path.isNotBlank()) {
                    _qrDestinationPath.value = path
                }
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

    private suspend fun receiveCrocKey(crocCode: String): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val crocBinary = File(nativeLibDir, Constants.BUNDLED_CROC_LIB)
        if (!crocBinary.exists() || !crocBinary.canExecute()) {
            timber.log.Timber.e("Croc binary not found or not executable: ${crocBinary.absolutePath}")
            return null
        }

        return withContext(Dispatchers.IO) {
            val receiveDir = File(context.filesDir, "croc_receive")
            receiveDir.listFiles()?.forEach { it.delete() }
            receiveDir.mkdirs()

            timber.log.Timber.d("Croc receive: code=$crocCode, out=${receiveDir.absolutePath}")

            val pb = ProcessBuilder(
                crocBinary.absolutePath, "--yes", "--overwrite", "--out", receiveDir.absolutePath
            )
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["CROC_SECRET"] = crocCode
            pb.redirectErrorStream(true)
            pb.directory(receiveDir)
            val process = pb.start()

            // Read process output for debugging
            val output = process.inputStream.bufferedReader().readText()
            timber.log.Timber.d("Croc output: $output")

            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                timber.log.Timber.e("Croc timed out")
                process.destroyForcibly()
            }

            val exitCode = try { process.exitValue() } catch (_: Exception) { -1 }
            timber.log.Timber.d("Croc exit code: $exitCode")

            val files = receiveDir.listFiles()
            timber.log.Timber.d("Croc received files: ${files?.map { "${it.name} (${it.length()})" }}")

            val receivedFile = files?.firstOrNull { it.isFile && it.length() > 0 }
            if (receivedFile != null) {
                val keyBytes = receivedFile.readBytes()
                val keyName = "croc_${System.currentTimeMillis()}"
                timber.log.Timber.d("Importing key: $keyName (${keyBytes.size} bytes)")
                sshKeyManager.importKey(keyName, keyBytes)
                val path = sshKeyManager.getPrivateKeyPath(keyName)
                receivedFile.delete()
                path
            } else {
                timber.log.Timber.e("No file received via croc")
                null
            }
        }
    }
}
