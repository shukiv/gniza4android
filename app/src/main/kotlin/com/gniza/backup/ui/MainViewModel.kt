package com.gniza.backup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gniza.backup.data.repository.ServerRepository
import com.gniza.backup.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _initialStartDestination = MutableStateFlow<String?>(null)
    val initialStartDestination: StateFlow<String?> = _initialStartDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val count = serverRepository.serverCount.first()
            _initialStartDestination.value = if (count == 0) {
                Screen.SetupWizard.route
            } else {
                Screen.ScheduleList.route
            }
        }
    }
}
