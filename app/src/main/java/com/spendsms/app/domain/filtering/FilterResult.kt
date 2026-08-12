package com.spendsms.app.domain.filtering

import com.spendsms.app.domain.sms.RawSmsMessage

/**
 * Outcome of the Step-3 message pre-filter.
 *
 * Rejected messages are dropped before parsing. [Accept] retains the ephemeral
 * SMS only for in-memory pipeline use — never persist or log the body.
 */
sealed interface FilterResult {
    data object Reject : FilterResult

    data class Accept(val message: RawSmsMessage) : FilterResult {
        override fun toString(): String =
            "Accept(sourceMessageId=${message.sourceMessageId}, sender=${message.sender})"
    }
}

/**
 * Identifies likely financial SMS and rejects obvious non-transactional content.
 */
interface FinancialMessageFilter {
    fun filter(
        message: RawSmsMessage,
        knownSenderPatterns: Collection<String>,
    ): FilterResult
}
