package com.spendsms.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.data.preferences.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface BootstrapDestination {
    data object Loading : BootstrapDestination
    data object Onboarding : BootstrapDestination
    data object ScanPeriod : BootstrapDestination
    data object Dashboard : BootstrapDestination
}

@HiltViewModel
class BootstrapViewModel @Inject constructor(
    private val preferences: UserPreferencesStore,
    private val scanStateRepository: ScanStateRepository,
    private val permissionPort: SmsPermissionPort,
    private val controlPlane: ControlPlaneCoordinator,
) : ViewModel() {

    private val _destination = MutableStateFlow<BootstrapDestination>(BootstrapDestination.Loading)
    val destination: StateFlow<BootstrapDestination> = _destination.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            launch { controlPlane.bootstrap() }
            val onboarded = preferences.onboardingCompleted.first()
            if (!onboarded) {
                _destination.value = BootstrapDestination.Onboarding
                return@launch
            }
            val completed = scanStateRepository.findLatestCompleted()
            val resumable = scanStateRepository.findResumable()
            _destination.value = when {
                resumable != null -> BootstrapDestination.ScanPeriod
                completed != null -> BootstrapDestination.Dashboard
                permissionPort.hasReadSmsPermission() -> BootstrapDestination.ScanPeriod
                else -> BootstrapDestination.Onboarding
            }
        }
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferencesStore,
    private val permissionPort: SmsPermissionPort,
) : ViewModel() {

    private val _permissionGranted = MutableStateFlow(permissionPort.hasReadSmsPermission())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    fun refreshPermission() {
        _permissionGranted.value = permissionPort.hasReadSmsPermission()
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            onDone()
        }
    }
}
