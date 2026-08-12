package com.spendsms.app.domain.model

/**
 * Inclusive analysis window selected by the user before a scan.
 */
data class AnalysisPeriod(
    val start: EpochMillis,
    val end: EpochMillis,
) {
    init {
        require(start <= end) {
            "AnalysisPeriod start must be <= end (start=${start.toEpochMillis}, end=${end.toEpochMillis})"
        }
    }
}
