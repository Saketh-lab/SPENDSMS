package com.spendsms.app.application.analysis

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.domain.model.ScanStatus
import org.junit.Test

class ScanWorkOutcomeMapperTest {

    private val period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(9L))
    private val state = ScanState(
        id = ScanId.of("scan-1"),
        period = period,
        lastProcessedMessageId = "42",
        parserVersion = ParserVersion.of("v1"),
        status = ScanStatus.INTERRUPTED,
        processedCount = 2,
        acceptedCount = 1,
        startedAt = EpochMillis.of(10L),
        completedAt = null,
        updatedAt = EpochMillis.of(11L),
    )

    @Test
    fun completed_isSuccess() {
        val outcome = ScanWorkOutcomeMapper.fromScanResult(
            ScanResult.Completed(state.copy(status = ScanStatus.COMPLETED, completedAt = EpochMillis.of(12L), updatedAt = EpochMillis.of(12L)), 0),
        )
        assertThat(outcome).isInstanceOf(ScanWorkOutcome.Success::class.java)
        assertThat((outcome as ScanWorkOutcome.Success).status).isEqualTo("COMPLETED")
    }

    @Test
    fun cancelled_isSuccessNotRetry() {
        val outcome = ScanWorkOutcomeMapper.fromScanResult(
            ScanResult.Cancelled(state.copy(status = ScanStatus.CANCELLED, completedAt = EpochMillis.of(12L), updatedAt = EpochMillis.of(12L)), 0),
        )
        assertThat(outcome).isInstanceOf(ScanWorkOutcome.Success::class.java)
        assertThat((outcome as ScanWorkOutcome.Success).status).isEqualTo("CANCELLED")
    }

    @Test
    fun permissionRevoked_isNonRetryableFailure() {
        val outcome = ScanWorkOutcomeMapper.fromScanResult(
            ScanResult.Interrupted(state, 0, ScanInterruptReason.PERMISSION_REVOKED),
        )
        assertThat(outcome).isEqualTo(
            ScanWorkOutcome.Failure(scanId = state.id, reason = "PERMISSION_REVOKED"),
        )
    }

    @Test
    fun providerError_isRetry() {
        val outcome = ScanWorkOutcomeMapper.fromScanResult(
            ScanResult.Interrupted(state, 0, ScanInterruptReason.PROVIDER_ERROR),
        )
        assertThat(outcome).isEqualTo(
            ScanWorkOutcome.Retry(scanId = state.id, reason = "PROVIDER_ERROR"),
        )
    }

    @Test
    fun permissionDenied_isNonRetryableFailure() {
        val outcome = ScanWorkOutcomeMapper.fromScanResult(
            ScanResult.Failed(null, ScanFailureReason.PERMISSION_DENIED, "denied"),
        )
        assertThat(outcome).isEqualTo(
            ScanWorkOutcome.Failure(scanId = null, reason = "PERMISSION_DENIED"),
        )
    }

    @Test
    fun parserUnavailable_isRetry() {
        val outcome = ScanWorkOutcomeMapper.fromScanResult(
            ScanResult.Failed(null, ScanFailureReason.PARSER_UNAVAILABLE, "missing"),
        )
        assertThat(outcome).isInstanceOf(ScanWorkOutcome.Retry::class.java)
    }

    @Test
    fun unexpected_isRetry() {
        val outcome = ScanWorkOutcomeMapper.fromScanResult(
            ScanResult.Failed(state.copy(status = ScanStatus.FAILED, completedAt = EpochMillis.of(12L), updatedAt = EpochMillis.of(12L)), ScanFailureReason.UNEXPECTED, "boom"),
        )
        assertThat(outcome).isInstanceOf(ScanWorkOutcome.Retry::class.java)
    }
}
