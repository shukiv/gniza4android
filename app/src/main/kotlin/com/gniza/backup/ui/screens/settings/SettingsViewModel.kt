package com.gniza.backup.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.preferences.AppPreferences
import com.gniza.backup.service.rsync.RsyncBinaryResolver
import com.gniza.backup.service.ssh.SshBinaryResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val rsyncBinaryResolver: RsyncBinaryResolver,
    private val sshBinaryResolver: SshBinaryResolver
) : ViewModel() {

    val rsyncBinaryPath: StateFlow<String> = appPreferences.rsyncBinaryPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val wifiOnly: StateFlow<Boolean> = appPreferences.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val logRetentionDays: StateFlow<Int> = appPreferences.logRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val darkThemeMode: StateFlow<String> = appPreferences.darkThemeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val notificationsEnabled: StateFlow<Boolean> = appPreferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _rsyncResult = MutableStateFlow<RsyncBinaryResolver.RsyncBinaryResult?>(null)
    val rsyncResult: StateFlow<RsyncBinaryResolver.RsyncBinaryResult?> = _rsyncResult.asStateFlow()

    private val _isCheckingRsync = MutableStateFlow(false)
    val isCheckingRsync: StateFlow<Boolean> = _isCheckingRsync.asStateFlow()

    private val _sshResult = MutableStateFlow<SshBinaryResolver.SshBinaryResult?>(null)
    val sshResult: StateFlow<SshBinaryResolver.SshBinaryResult?> = _sshResult.asStateFlow()

    private val _isCheckingSsh = MutableStateFlow(false)
    val isCheckingSsh: StateFlow<Boolean> = _isCheckingSsh.asStateFlow()

    init {
        checkRsyncAvailability()
        checkSshAvailability()
    }

    fun setRsyncPath(path: String) {
        viewModelScope.launch {
            appPreferences.setRsyncBinaryPath(path)
        }
    }

    fun setTheme(mode: String) {
        viewModelScope.launch {
            appPreferences.setDarkThemeMode(mode)
        }
    }

    fun setLogRetention(days: Int) {
        viewModelScope.launch {
            appPreferences.setLogRetentionDays(days)
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setWifiOnly(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setNotificationsEnabled(enabled)
        }
    }

    fun checkRsyncAvailability() {
        viewModelScope.launch {
            _isCheckingRsync.value = true
            val customPath = appPreferences.rsyncBinaryPath.first()
            _rsyncResult.value = rsyncBinaryResolver.resolve(
                userOverridePath = customPath.ifBlank { null }
            )
            _isCheckingRsync.value = false
        }
    }

    fun checkSshAvailability() {
        viewModelScope.launch {
            _isCheckingSsh.value = true
            _sshResult.value = sshBinaryResolver.resolve()
            _isCheckingSsh.value = false
        }
    }
}
