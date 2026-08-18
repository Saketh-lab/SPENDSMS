package com.spendsms.app.presentation.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendsms.app.application.analysis.ScanCompletionHandler
import com.spendsms.app.application.analysis.ScanCoordinator
import com.spendsms.app.application.analysis.ScanFailureReason
import com.spendsms.app.application.analysis.ScanInterruptReason
import com.spendsms.app.application.analysis.ScanRequest
import com.spendsms.app.application.analysis.ScanResult
import com.spendsms.app.application.analysis.ScanScheduleRequest
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.ScanWorkScheduler
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.presentation.common.PeriodPreset
import com.spendsms.app.presentation.common.toAnalysisPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ScanUiProgress(
    val processedCount: Int = 0,
    val acceptedCount: Int = 0,
    val statusLabel: String = "Starting…",
)

sealed interface ScanPhase {
    data object SelectPeriod : ScanPhase
    data class Running(val progress: ScanUiProgress) : ScanPhase
    data class Completed(
        val processedCount: Int,
        val acceptedCount: Int,
        val isolatedFailures: Int,
        val period: AnalysisPeriod,
    ) : ScanPhase
    data class Failed(val message: String, val canResume: Boolean) : ScanPhase
    data class PermissionRequired(val message: String) : ScanPhase
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanCoordinator: ScanCoordinator,
    private val scanStateRepository: ScanStateRepository,
    private val permissionPort: SmsPermissionPort,
    private val preferences: UserPreferencesStore,
    private val completionHandler: ScanCompletionHandler,
    private val scanWorkScheduler: ScanWorkScheduler,
) : ViewModel() {

    private val _phase = MutableStateFlow<ScanPhase>(ScanPhase.SelectPeriod)
    val phase: StateFlow<ScanPhase> = _phase.asStateFlow()

    private val _selectedPreset = MutableStateFlow(PeriodPreset.LAST_30_DAYS)
    val selectedPreset: StateFlow<PeriodPreset> = _selectedPreset.asStateFlow()

    private val _resumable = MutableStateFlow<ScanState?>(null)
    val resumable: StateFlow<ScanState?> = _resumable.asStateFlow()

    private var scanJob: Job? = null
    private var activePeriod: AnalysisPeriod? = null

    init {
        viewModelScope.launch {
            val leftover = scanStateRepository.findResumable()
            _resumable.value = leftover
            preferences.lastAnalysisPeriod.first()?.let { /* keep prefs warm */ }
            if (leftover != null) {
                resumeIfPossible()
            }
        }
    }

    fun selectPreset(preset: PeriodPreset) {
        _selectedPreset.value = preset
    }

    fun startSelectedPeriod() {
        startScan(_selectedPreset.value.toAnalysisPeriod())
    }

    fun resumeIfPossible() {
        viewModelScope.launch {
            val existing = scanStateRepository.findResumable() ?: _resumable.value ?: return@launch
            _resumable.value = existing
            startScan(existing.period, resumeScanId = existing.id.value)
        }
    }

    fun cancelScan() {
        viewModelScope.launch {
            scanWorkScheduler.cancel()
        }
    }

    fun resetToPeriodSelection() {
        scanJob?.cancel()
        _phase.value = ScanPhase.SelectPeriod
        viewModelScope.launch {
            _resumable.value = scanStateRepository.findResumable()
        }
    }

    private fun startScan(period: AnalysisPeriod, resumeScanId: String? = null) {
        if (!permissionPort.hasReadSmsPermission()) {
            _phase.value = ScanPhase.PermissionRequired(
                "READ_SMS permission is required to analyse inbox alerts.",
            )
            return
        }
        activePeriod = period
        scanJob?.cancel()
        _phase.value = ScanPhase.Running(ScanUiProgress())
        scanJob = viewModelScope.launch {
            runCatching {
                scanWorkScheduler.enqueue(
                    ScanScheduleRequest(
                        period = period,
                        resumeScanId = parseResumeScanId(resumeScanId),
                    ),
                )
            }
            val result = scanCoordinator.startScan(
                request = ScanRequest(
                    period = period,
                    resumeScanId = parseResumeScanId(resumeScanId),
                ),
            ) { state ->
                _phase.value = ScanPhase.Running(
                    ScanUiProgress(
                        processedCount = state.processedCount,
                        acceptedCount = state.acceptedCount,
                        statusLabel = state.status.name,
                    ),
                )
            }
            handleResult(result, period)
        }
    }

    private suspend fun handleResult(result: ScanResult, period: AnalysisPeriod) {
        when (result) {
            is ScanResult.Completed -> {
                completionHandler.onScanCompleted(
                    period = period,
                    now = EpochMillis.of(System.currentTimeMillis()),
                )
                _phase.value = ScanPhase.Completed(
                    processedCount = result.state.processedCount,
                    acceptedCount = result.state.acceptedCount,
                    isolatedFailures = result.isolatedFailureCount,
                    period = period,
                )
            }
            is ScanResult.Cancelled -> {
                _phase.value = ScanPhase.Failed(
                    message = "Scan cancelled. Progress was saved.",
                    canResume = false,
                )
            }
            is ScanResult.Interrupted -> {
                _resumable.value = result.state
                val message = when (result.reason) {
                    ScanInterruptReason.PERMISSION_REVOKED ->
                        "SMS permission was revoked. Restore access to resume."
                    ScanInterruptReason.PROVIDER_ERROR ->
                        "SMS provider error. You can resume from the last checkpoint."
                    ScanInterruptReason.COROUTINE_CANCELLED ->
                        "Scan interrupted. You can resume from the last checkpoint."
                }
                _phase.value = ScanPhase.Failed(message = message, canResume = true)
            }
            is ScanResult.Failed -> {
                if (result.state != null &&
                    (result.reason == ScanFailureReason.PERMISSION_DENIED ||
                        result.reason == ScanFailureReason.SCAN_ALREADY_ACTIVE)
                ) {
                    _resumable.value = scanStateRepository.findResumable() ?: result.state
                }
                val message = when (result.reason) {
                    ScanFailureReason.PERMISSION_DENIED ->
                        "SMS permission is not granted."
                    ScanFailureReason.PARSER_UNAVAILABLE ->
                        "Parser rules are unavailable. Bundled rules may need reinstall."
                    ScanFailureReason.SCAN_ALREADY_ACTIVE ->
                        "A scan is already in progress. You can resume it."
                    ScanFailureReason.NOT_RESUMABLE ->
                        "This scan cannot be resumed."
                    ScanFailureReason.UNEXPECTED ->
                        result.detail
                }
                _phase.value = ScanPhase.Failed(
                    message = message,
                    canResume = result.reason == ScanFailureReason.PERMISSION_DENIED ||
                        result.reason == ScanFailureReason.SCAN_ALREADY_ACTIVE,
                )
            }
        }
    }

    private fun parseResumeScanId(raw: String?): ScanId? =
        raw?.takeIf { it.isNotBlank() }?.let { ScanId.of(it) }
}
