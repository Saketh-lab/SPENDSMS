package com.spendsms.app.application.port.telemetry

import com.spendsms.app.domain.model.EpochMillis

/**
 * Phase-0 allow-listed telemetry event types (Step-4).
 */
enum class TelemetryEventType {
    SCAN_STARTED,
    SCAN_COMPLETED,
    SCAN_FAILED,
    PARSER_UPDATE_CHECKED,
    PARSER_UPDATE_ACTIVATED,
    PARSER_UPDATE_FAILED,
    APP_ERROR,
    ;

    fun wireValue(): String = when (this) {
        SCAN_STARTED -> "scan_started"
        SCAN_COMPLETED -> "scan_completed"
        SCAN_FAILED -> "scan_failed"
        PARSER_UPDATE_CHECKED -> "parser_update_checked"
        PARSER_UPDATE_ACTIVATED -> "parser_update_activated"
        PARSER_UPDATE_FAILED -> "parser_update_failed"
        APP_ERROR -> "app_error"
    }
}

enum class TelemetryDurationBucket {
    LT_1S,
    S_1_5,
    S_5_15,
    S_15_30,
    S_30_60,
    M_1_5,
    GT_5M,
    ;

    fun wireValue(): String = when (this) {
        LT_1S -> "LT_1S"
        S_1_5 -> "1S_5S"
        S_5_15 -> "5S_15S"
        S_15_30 -> "15S_30S"
        S_30_60 -> "30S_60S"
        M_1_5 -> "1M_5M"
        GT_5M -> "GT_5M"
    }
}

enum class TelemetryCountBucket {
    ZERO,
    FROM_1_TO_10,
    FROM_11_TO_50,
    FROM_51_TO_200,
    FROM_201_TO_1000,
    FROM_1001_PLUS,
    ;

    fun wireValue(): String = when (this) {
        ZERO -> "0"
        FROM_1_TO_10 -> "1_10"
        FROM_11_TO_50 -> "11_50"
        FROM_51_TO_200 -> "51_200"
        FROM_201_TO_1000 -> "201_1000"
        FROM_1001_PLUS -> "1001_PLUS"
    }
}

/**
 * Coarse operational event. Must never include SMS body, amounts, merchants,
 * accounts, categories, or other financial payloads (Step-4).
 */
data class TelemetryEvent(
    val eventId: String,
    val occurredAt: EpochMillis,
    val eventType: TelemetryEventType,
    val durationBucket: TelemetryDurationBucket? = null,
    val countBucket: TelemetryCountBucket? = null,
    val errorCode: String? = null,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        errorCode?.let {
            require(ERROR_CODE_PATTERN.matches(it)) {
                "errorCode must match ^[A-Z0-9_]{3,64}$, was '$it'"
            }
        }
    }

    companion object {
        private val ERROR_CODE_PATTERN = Regex("^[A-Z0-9_]{3,64}$")
    }
}

/**
 * Privacy-safe telemetry outbox / client port.
 *
 * Must never block scanning, dashboard, corrections, or deletion.
 */
interface TelemetryPort {

    /** Enqueue allow-listed events into a bounded local outbox. */
    suspend fun enqueue(events: List<TelemetryEvent>)

    /** Best-effort flush; failures are non-fatal to product flows. */
    suspend fun flush()
}
