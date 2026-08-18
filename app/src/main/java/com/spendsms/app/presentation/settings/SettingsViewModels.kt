package com.spendsms.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.FinancialDataDeletionPort
import com.spendsms.app.application.port.ParserBundleRepository
import com.spendsms.app.application.port.ScanWorkScheduler
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.controlplane.ControlPlaneStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUi(
    val permissionGranted: Boolean,
    val appVersionLabel: String,
    val controlPlane: ControlPlaneStatus,
)

sealed interface DeletionPhase {
    data object Confirm : DeletionPhase
    data object Deleting : DeletionPhase
    data object Done : DeletionPhase
    data class Failed(val message: String) : DeletionPhase
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val permissionPort: SmsPermissionPort,
    private val controlPlane: ControlPlaneCoordinator,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        SettingsUi(
            permissionGranted = permissionPort.hasReadSmsPermission(),
            appVersionLabel = com.spendsms.app.BuildConfig.VERSION_NAME,
            controlPlane = ControlPlaneStatus.localOnly(),
        ),
    )
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = controlPlane.currentStatus()
            _ui.value = _ui.value.copy(
                permissionGranted = permissionPort.hasReadSmsPermission(),
                controlPlane = status,
            )
        }
    }

    fun refreshPermission() {
        _ui.value = _ui.value.copy(permissionGranted = permissionPort.hasReadSmsPermission())
    }
}

@HiltViewModel
class DataDeletionViewModel @Inject constructor(
    private val deletionPort: FinancialDataDeletionPort,
    private val dashboardService: DashboardService,
    private val preferences: UserPreferencesStore,
    private val scanWorkScheduler: ScanWorkScheduler,
    private val parserBundleRepository: ParserBundleRepository,
) : ViewModel() {

    private val _phase = MutableStateFlow<DeletionPhase>(DeletionPhase.Confirm)
    val phase: StateFlow<DeletionPhase> = _phase.asStateFlow()

    private val _parserStillPresent = MutableStateFlow(false)
    val parserStillPresent: StateFlow<Boolean> = _parserStillPresent.asStateFlow()

    fun deleteAnalysedData() {
        viewModelScope.launch {
            _phase.value = DeletionPhase.Deleting
            runCatching {
                scanWorkScheduler.cancel()
                deletionPort.deleteAllAnalysedData()
                dashboardService.invalidateCache()
                preferences.clearAnalysisPeriod()
                _parserStillPresent.value = parserBundleRepository.findActiveMetadata() != null
            }.onSuccess {
                _phase.value = DeletionPhase.Done
            }.onFailure { error ->
                _phase.value = DeletionPhase.Failed(error.message ?: "Deletion failed")
            }
        }
    }

    fun reset() {
        _phase.value = DeletionPhase.Confirm
    }
}
