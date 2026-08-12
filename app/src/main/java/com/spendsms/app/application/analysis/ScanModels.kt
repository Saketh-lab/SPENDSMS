package com.spendsms.app.application.analysis

import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState

const val DEFAULT_SCAN_BATCH_SIZE: Int = 50

data class ScanRequest(
    val period: AnalysisPeriod,
    val resumeScanId: ScanId? = null,
    val batchSize: Int = DEFAULT_SCAN_BATCH_SIZE,
) {
    init {
        require(batchSize > 0) { "batchSize must be > 0" }
    }
}

fun interface ScanProgressListener {
    fun onProgress(state: ScanState)
}

sealed class ScanResult {
    abstract val state: ScanState?

    data class Completed(
        override val state: ScanState,
        val isolatedFailureCount: Int,
    ) : ScanResult()

    data class Cancelled(
        override val state: ScanState,
        val isolatedFailureCount: Int,
    ) : ScanResult()

    data class Interrupted(
        override val state: ScanState,
        val isolatedFailureCount: Int,
        val reason: ScanInterruptReason,
    ) : ScanResult()

    data class Failed(
        override val state: ScanState?,
        val reason: ScanFailureReason,
        val detail: String,
    ) : ScanResult()
}

enum class ScanInterruptReason {
    PERMISSION_REVOKED,
    PROVIDER_ERROR,
    COROUTINE_CANCELLED,
}

enum class ScanFailureReason {
    PERMISSION_DENIED,
    PARSER_UNAVAILABLE,
    SCAN_ALREADY_ACTIVE,
    NOT_RESUMABLE,
    UNEXPECTED,
}
