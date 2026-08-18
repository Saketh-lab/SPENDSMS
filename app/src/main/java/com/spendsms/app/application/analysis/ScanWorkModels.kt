package com.spendsms.app.application.analysis

import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.ScanId

data class ScanWorkInput(
    val period: AnalysisPeriod,
    val resumeScanId: ScanId? = null,
    val batchSize: Int = DEFAULT_SCAN_BATCH_SIZE,
) {
    init {
        require(batchSize > 0) { "batchSize must be > 0" }
    }
}

data class ScanScheduleRequest(
    val period: AnalysisPeriod,
    val resumeScanId: ScanId? = null,
    val batchSize: Int = DEFAULT_SCAN_BATCH_SIZE,
) {
    init {
        require(batchSize > 0) { "batchSize must be > 0" }
    }

    fun toWorkInput(): ScanWorkInput = ScanWorkInput(
        period = period,
        resumeScanId = resumeScanId,
        batchSize = batchSize,
    )
}

sealed class ScanScheduleResult {
    data class Enqueued(val workId: String) : ScanScheduleResult()
    data class AlreadyScheduled(val workId: String) : ScanScheduleResult()
}

sealed class ScanWorkOutcome {
    abstract val scanId: ScanId?
    abstract val reason: String?

    data class Success(
        override val scanId: ScanId?,
        val status: String,
        override val reason: String? = null,
    ) : ScanWorkOutcome()

    data class Retry(
        override val scanId: ScanId?,
        override val reason: String,
    ) : ScanWorkOutcome()

    data class Failure(
        override val scanId: ScanId?,
        override val reason: String,
    ) : ScanWorkOutcome()
}

object ScanWorkOutcomeMapper {

    fun fromScanResult(result: ScanResult): ScanWorkOutcome = when (result) {
        is ScanResult.Completed -> ScanWorkOutcome.Success(
            scanId = result.state.id,
            status = "COMPLETED",
        )
        is ScanResult.Cancelled -> ScanWorkOutcome.Success(
            scanId = result.state.id,
            status = "CANCELLED",
        )
        is ScanResult.Interrupted -> when (result.reason) {
            ScanInterruptReason.PERMISSION_REVOKED -> ScanWorkOutcome.Failure(
                scanId = result.state.id,
                reason = "PERMISSION_REVOKED",
            )
            ScanInterruptReason.PROVIDER_ERROR -> ScanWorkOutcome.Retry(
                scanId = result.state.id,
                reason = "PROVIDER_ERROR",
            )
            ScanInterruptReason.COROUTINE_CANCELLED -> ScanWorkOutcome.Retry(
                scanId = result.state.id,
                reason = "COROUTINE_CANCELLED",
            )
        }
        is ScanResult.Failed -> when (result.reason) {
            ScanFailureReason.PERMISSION_DENIED -> ScanWorkOutcome.Failure(
                scanId = result.state?.id,
                reason = "PERMISSION_DENIED",
            )
            ScanFailureReason.PARSER_UNAVAILABLE -> ScanWorkOutcome.Failure(
                scanId = result.state?.id,
                reason = "PARSER_UNAVAILABLE",
            )
            ScanFailureReason.SCAN_ALREADY_ACTIVE -> ScanWorkOutcome.Retry(
                scanId = result.state?.id,
                reason = "SCAN_ALREADY_ACTIVE",
            )
            ScanFailureReason.NOT_RESUMABLE -> ScanWorkOutcome.Failure(
                scanId = result.state?.id,
                reason = "NOT_RESUMABLE",
            )
            ScanFailureReason.UNEXPECTED -> ScanWorkOutcome.Retry(
                scanId = result.state?.id,
                reason = "UNEXPECTED",
            )
        }
    }
}
