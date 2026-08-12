package com.spendsms.app.domain.model

/**
 * Persisted scan progress for resumable, idempotent analysis (Step-3 `scan_state`).
 *
 * [lastProcessedMessageId] is a local SMS provider cursor/id — not raw message body.
 */
data class ScanState(
    val id: ScanId,
    val period: AnalysisPeriod,
    val lastProcessedMessageId: String?,
    val parserVersion: ParserVersion,
    val status: ScanStatus,
    val processedCount: Int,
    val acceptedCount: Int,
    val startedAt: EpochMillis,
    val completedAt: EpochMillis?,
    val updatedAt: EpochMillis,
) {
    init {
        lastProcessedMessageId?.let {
            require(it.isNotBlank()) {
                "lastProcessedMessageId, when present, must not be blank"
            }
        }
        require(processedCount >= 0) { "processedCount must be >= 0" }
        require(acceptedCount >= 0) { "acceptedCount must be >= 0" }
        require(acceptedCount <= processedCount) {
            "acceptedCount ($acceptedCount) must be <= processedCount ($processedCount)"
        }
        require(updatedAt >= startedAt) { "updatedAt must be >= startedAt" }

        when (status) {
            ScanStatus.COMPLETED -> {
                require(completedAt != null) {
                    "completedAt is required when status is COMPLETED"
                }
                require(completedAt >= startedAt) {
                    "completedAt must be >= startedAt"
                }
            }
            ScanStatus.PENDING,
            ScanStatus.RUNNING,
            -> {
                require(completedAt == null) {
                    "completedAt must be null while scan is $status"
                }
            }
            ScanStatus.INTERRUPTED,
            ScanStatus.CANCELLED,
            ScanStatus.FAILED,
            -> {
                // completedAt may be set to the terminal timestamp or left null.
            }
        }
    }

    val isActive: Boolean
        get() = status == ScanStatus.PENDING || status == ScanStatus.RUNNING
}
