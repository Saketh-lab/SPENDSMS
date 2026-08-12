package com.spendsms.app.domain.sms

import com.spendsms.app.domain.model.EpochMillis

/**
 * In-memory SMS snapshot for filtering/parsing only.
 *
 * [body] must never be persisted, logged, or uploaded. Discard after the
 * pipeline finishes with this instance.
 */
data class RawSmsMessage(
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

    override fun toString(): String =
        "RawSmsMessage(sourceMessageId=$sourceMessageId, sender=$sender, receivedAt=$receivedAt, body=***)"
}
