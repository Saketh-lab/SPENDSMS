package com.spendsms.app.presentation.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.controlplane.ControlPlaneStatus
import com.spendsms.app.application.controlplane.SupportSubmissionUiResult
import com.spendsms.app.application.port.support.RedactedUnsupportedFormatSubmission
import com.spendsms.app.application.port.support.UnsupportedFormatReason
import com.spendsms.app.domain.model.EpochMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SupportUiState {
    data object Loading : SupportUiState
    data class Unavailable(
        val controlPlane: ControlPlaneStatus,
    ) : SupportUiState
    data class Ready(
        val controlPlane: ControlPlaneStatus,
    ) : SupportUiState
    data object Submitting : SupportUiState
    data class Submitted(
        val submissionId: String,
    ) : SupportUiState
    data object ServiceUnavailable : SupportUiState
}

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val controlPlane: ControlPlaneCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow<SupportUiState>(SupportUiState.Loading)
    val state: StateFlow<SupportUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = controlPlane.currentStatus()
            _state.value = if (status.supportSubmissionEnabled) {
                SupportUiState.Ready(status)
            } else {
                SupportUiState.Unavailable(status)
            }
        }
    }

    fun submitSampleRedactedTemplate() {
        viewModelScope.launch {
            val current = _state.value
            if (current is SupportUiState.Unavailable || current is SupportUiState.Loading) {
                return@launch
            }
            _state.value = SupportUiState.Submitting
            val submission = RedactedUnsupportedFormatSubmission(
                submissionId = UUID.randomUUID().toString(),
                idempotencyKey = UUID.randomUUID().toString(),
                consentedAt = EpochMillis.of(System.currentTimeMillis()),
                previewConfirmed = true,
                redactionVersion = "phase0-local",
                reason = UnsupportedFormatReason.NO_RULE_MATCH,
                redactedTemplate = "Debited [AMOUNT] at [MERCHANT]",
                parserVersion = null,
            )
            when (val result = controlPlane.submitSupport(submission)) {
                SupportSubmissionUiResult.FeatureDisabled,
                SupportSubmissionUiResult.Unavailable,
                -> _state.value = SupportUiState.ServiceUnavailable
                is SupportSubmissionUiResult.Accepted ->
                    _state.value = SupportUiState.Submitted(result.submissionId)
                is SupportSubmissionUiResult.Rejected ->
                    _state.value = SupportUiState.ServiceUnavailable
            }
        }
    }
}
