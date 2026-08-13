package com.spendsms.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.FinancialDataDeletionPort
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.data.preferences.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUi(
    val permissionGranted: Boolean,
    val appVersionLabel: String,
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
) : ViewModel() {

    private val _ui = MutableStateFlow(
        SettingsUi(
            permissionGranted = permissionPort.hasReadSmsPermission(),
            appVersionLabel = com.spendsms.app.BuildConfig.VERSION_NAME,
        ),
    )
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    fun refreshPermission() {
        _ui.value = _ui.value.copy(permissionGranted = permissionPort.hasReadSmsPermission())
    }
}

@HiltViewModel
class DataDeletionViewModel @Inject constructor(
    private val deletionPort: FinancialDataDeletionPort,
    private val dashboardService: DashboardService,
    private val preferences: UserPreferencesStore,
) : ViewModel() {

    private val _phase = MutableStateFlow<DeletionPhase>(DeletionPhase.Confirm)
    val phase: StateFlow<DeletionPhase> = _phase.asStateFlow()

    private val _parserStillPresent = MutableStateFlow(false)
    val parserStillPresent: StateFlow<Boolean> = _parserStillPresent.asStateFlow()

    fun deleteAnalysedData() {
        viewModelScope.launch {
            _phase.value = DeletionPhase.Deleting
            runCatching {
                deletionPort.deleteAllAnalysedData()
                dashboardService.invalidateCache()
                preferences.clearAnalysisPeriod()
                // Financial wipe retains parser_metadata and bundled rule assets.
                _parserStillPresent.value = true
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
