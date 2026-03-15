package com.gniza.backup.ui.screens.wizard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.repository.BackupSourceRepository
import com.gniza.backup.data.repository.ScheduleRepository
import com.gniza.backup.data.repository.ServerRepository
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.BackupSource
import com.gniza.backup.domain.model.Schedule
import com.gniza.backup.domain.model.ScheduleInterval
import com.gniza.backup.domain.model.Server
import com.gniza.backup.service.ssh.SshKeyManager
import com.gniza.backup.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val backupSourceRepository: BackupSourceRepository,
    private val scheduleRepository: ScheduleRepository,
    private val sshKeyManager: SshKeyManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _serverName = MutableStateFlow("")
    val serverName: StateFlow<String> = _serverName.asStateFlow()
    private val _serverHost = MutableStateFlow("")
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()
    private val _serverPort = MutableStateFlow(22)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()
    private val _serverUsername = MutableStateFlow("")
    val serverUsername: StateFlow<String> = _serverUsername.asStateFlow()
    private val _serverAuthMethod = MutableStateFlow(AuthMethod.PASSWORD)
    val serverAuthMethod: StateFlow<AuthMethod> = _serverAuthMethod.asStateFlow()
    private val _serverPassword = MutableStateFlow("")
    val serverPassword: StateFlow<String> = _serverPassword.asStateFlow()

    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()
    private val _sourceFolders = MutableStateFlow<List<String>>(emptyList())
    val sourceFolders: StateFlow<List<String>> = _sourceFolders.asStateFlow()

    private val _scheduleName = MutableStateFlow("")
    val scheduleName: StateFlow<String> = _scheduleName.asStateFlow()
    private val _scheduleDestinationPath = MutableStateFlow("")
    val scheduleDestinationPath: StateFlow<String> = _scheduleDestinationPath.asStateFlow()
    private val _scheduleInterval = MutableStateFlow(ScheduleInterval.DAILY)
    val scheduleInterval: StateFlow<ScheduleInterval> = _scheduleInterval.asStateFlow()

    private val _sshKeyGenerated = MutableStateFlow(false)
    val sshKeyGenerated: StateFlow<Boolean> = _sshKeyGenerated.asStateFlow()
    private val _sshPublicKey = MutableStateFlow("")
    val sshPublicKey: StateFlow<String> = _sshPublicKey.asStateFlow()
    private var generatedKeyName: String = ""

    private var savedServerId: Long = 0
    private var savedSourceId: Long = 0

    fun updateServerName(v: String) { _serverName.value = v }
    fun updateServerHost(v: String) { _serverHost.value = v }
    fun updateServerPort(v: Int) { _serverPort.value = v }
    fun updateServerUsername(v: String) { _serverUsername.value = v }
    fun updateServerAuthMethod(v: AuthMethod) { _serverAuthMethod.value = v }
    fun updateServerPassword(v: String) { _serverPassword.value = v }
    fun updateSourceName(v: String) { _sourceName.value = v }
    fun updateSourceFolders(v: List<String>) { _sourceFolders.value = v }
    fun updateScheduleName(v: String) { _scheduleName.value = v }
    fun updateScheduleDestinationPath(v: String) { _scheduleDestinationPath.value = v }
    fun updateScheduleInterval(v: ScheduleInterval) { _scheduleInterval.value = v }

    fun applyQrData(json: String) {
        viewModelScope.launch {
            try {
                val obj = org.json.JSONObject(json)
                if (!obj.has("gniza")) return@launch
                _serverHost.value = obj.optString("host", "")
                _serverPort.value = obj.optInt("port", 22)
                _serverUsername.value = obj.optString("user", "")
                _serverName.value = obj.optString("name", obj.optString("host", ""))
                val auth = obj.optString("auth", "ssh_key")
                _serverAuthMethod.value = if (auth == "password") AuthMethod.PASSWORD else AuthMethod.SSH_KEY
                if (auth == "password") _serverPassword.value = obj.optString("pass", "")
                val path = obj.optString("path", "")
                if (path.isNotBlank()) _scheduleDestinationPath.value = path

                // Import embedded private key
                if (obj.has("key")) {
                    val compressed = android.util.Base64.decode(obj.getString("key"), android.util.Base64.DEFAULT)
                    val keyBytes = java.util.zip.GZIPInputStream(compressed.inputStream()).readBytes()
                    val keyName = "qr_${System.currentTimeMillis()}"
                    sshKeyManager.importKey(keyName, keyBytes)
                    generatedKeyName = keyName
                    _sshPublicKey.value = sshKeyManager.getPublicKey(keyName)
                    _sshKeyGenerated.value = true
                }

                // Receive private key via croc transfer
                if (obj.has("croc")) {
                    val crocCode = obj.getString("croc")
                    val keyPath = receiveCrocKey(crocCode)
                    if (keyPath != null) {
                        val keyName = File(keyPath).name
                        generatedKeyName = keyName
                        _sshPublicKey.value = try { sshKeyManager.getPublicKey(keyName) } catch (_: Exception) { "Key imported" }
                        _sshKeyGenerated.value = true
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun generateSshKey() {
        viewModelScope.launch {
            val keyName = "wizard_${System.currentTimeMillis()}"
            sshKeyManager.generateKeyPair(keyName)
            generatedKeyName = keyName
            _sshPublicKey.value = sshKeyManager.getPublicKey(keyName)
            _sshKeyGenerated.value = true
        }
    }

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun nextStep() {
        viewModelScope.launch {
            _isSaving.value = true
            when (_currentStep.value) {
                0 -> _currentStep.value = 1
                1 -> {
                    val server = Server(
                        name = _serverName.value,
                        host = _serverHost.value,
                        port = _serverPort.value,
                        username = _serverUsername.value,
                        authMethod = _serverAuthMethod.value,
                        password = if (_serverAuthMethod.value == AuthMethod.PASSWORD) _serverPassword.value else null,
                        privateKeyPath = if (_serverAuthMethod.value == AuthMethod.SSH_KEY && generatedKeyName.isNotEmpty()) {
                            sshKeyManager.getPrivateKeyPath(generatedKeyName)
                        } else null
                    )
                    savedServerId = serverRepository.saveServer(server)
                    _currentStep.value = 2
                }
                2 -> {
                    val source = BackupSource(
                        name = _sourceName.value,
                        sourceFolders = _sourceFolders.value
                    )
                    savedSourceId = backupSourceRepository.saveSource(source)
                    if (_scheduleName.value.isBlank()) {
                        _scheduleName.value = "${_sourceName.value} backup"
                    }
                    _currentStep.value = 3
                }
                3 -> {
                    val schedule = Schedule(
                        name = _scheduleName.value,
                        sourceId = savedSourceId,
                        serverId = savedServerId,
                        destinationPath = _scheduleDestinationPath.value,
                        interval = _scheduleInterval.value,
                        enabled = true
                    )
                    scheduleRepository.saveSchedule(schedule)
                    _currentStep.value = 4
                }
            }
            _isSaving.value = false
        }
    }

    private suspend fun receiveCrocKey(crocCode: String): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val crocBinary = File(nativeLibDir, Constants.BUNDLED_CROC_LIB)
        if (!crocBinary.exists() || !crocBinary.canExecute()) return null

        return withContext(Dispatchers.IO) {
            val receiveDir = File(context.filesDir, "croc_receive")
            receiveDir.listFiles()?.forEach { it.delete() }
            receiveDir.mkdirs()

            val env = ProcessBuilder(
                crocBinary.absolutePath, "--yes", "--overwrite", "--out", receiveDir.absolutePath
            )
            env.environment()["HOME"] = context.filesDir.absolutePath
            env.environment()["CROC_SECRET"] = crocCode
            env.redirectErrorStream(true)
            val process = env.start()

            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()

            val receivedFile = receiveDir.listFiles()?.firstOrNull { it.isFile }
            if (receivedFile != null && receivedFile.exists() && receivedFile.length() > 0) {
                val keyBytes = receivedFile.readBytes()
                val keyName = "croc_${System.currentTimeMillis()}"
                sshKeyManager.importKey(keyName, keyBytes)
                val path = sshKeyManager.getPrivateKeyPath(keyName)
                receivedFile.delete()
                path
            } else {
                null
            }
        }
    }

    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value = _currentStep.value - 1
        }
    }
}
