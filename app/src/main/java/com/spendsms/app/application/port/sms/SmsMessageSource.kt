package com.spendsms.app.application.port.sms

import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis

/**
 * Ephemeral SMS record for the scan pipeline only (Step-3 SMS Source outputs).
 *
 * [body] must never be written to Room or uploaded to AWS. It exists only while
 * a batch is processed in memory.
 */
data class EphemeralSmsMessage(
    val sourceMessageId: String,
    val sender: String,
    val receivedAt: EpochMillis,
    val body: String,
) {
    init {
        require(sourceMessageId.isNotBlank()) { "sourceMessageId must not be blank" }
        require(sender.isNotBlank()) { "sender must not be blank" }
        require(body.isNotEmpty()) { "body must not be empty for processing" }
    }
}

data class SmsBatchQuery(
    val period: AnalysisPeriod,
    /** Resume cursor: exclusive lower bound on provider message id, when present. */
    val afterMessageId: String? = null,
    val limit: Int,
) {
    init {
        require(limit > 0) { "SmsBatchQuery.limit must be > 0" }
        afterMessageId?.let {
            require(it.isNotBlank()) { "afterMessageId, when present, must not be blank" }
        }
    }
}

data class SmsBatch(
    val messages: List<EphemeralSmsMessage>,
    val nextAfterMessageId: String?,
    val exhausted: Boolean,
)

/**
 * Read-only SMS access abstraction.
 *
 * Implementations must not modify, delete, send, or mark messages as read.
 * Cancellation is cooperative via coroutine cancellation.
 */
interface SmsMessageSource {

    /**
     * @throws SmsAccessException when permission is denied/revoked or the provider fails.
     */
    suspend fun readBatch(query: SmsBatchQuery): SmsBatch
}

/**
 * Actionable SMS access failures for the scan coordinator.
 */
sealed class SmsAccessException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class PermissionDenied(message: String = "SMS read permission denied") :
        SmsAccessException(message)

    class PermissionRevoked(message: String = "SMS read permission revoked during scan") :
        SmsAccessException(message)

    class ProviderError(
        message: String,
        cause: Throwable? = null,
    ) : SmsAccessException(message, cause)
}
