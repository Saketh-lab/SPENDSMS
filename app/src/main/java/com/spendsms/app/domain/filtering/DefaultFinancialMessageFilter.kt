package com.spendsms.app.domain.filtering

import com.spendsms.app.domain.parsing.SenderPatternMatcher
import com.spendsms.app.domain.sms.RawSmsMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fail-closed pre-filter: reject OTP/marketing/delivery/personal noise; accept
 * known financial senders or bodies with clear financial signals.
 */
@Singleton
class DefaultFinancialMessageFilter @Inject constructor() : FinancialMessageFilter {

    override fun filter(
        message: RawSmsMessage,
        knownSenderPatterns: Collection<String>,
    ): FilterResult {
        return try {
            evaluate(message, knownSenderPatterns)
        } catch (_: Exception) {
            FilterResult.Reject
        }
    }

    private fun evaluate(
        message: RawSmsMessage,
        knownSenderPatterns: Collection<String>,
    ): FilterResult {
        val body = message.body
        if (body.isBlank()) return FilterResult.Reject

        if (BundledFilterLexicon.strongReject.any { it.containsMatchIn(body) }) {
            return FilterResult.Reject
        }
        if (BundledFilterLexicon.deliveryReject.any { it.containsMatchIn(body) }) {
            return FilterResult.Reject
        }

        val senderKnown = SenderPatternMatcher.matchesAny(message.sender, knownSenderPatterns)
        val financialBody = BundledFilterLexicon.financialPositive.any { it.containsMatchIn(body) }

        if (BundledFilterLexicon.marketingReject.any { it.containsMatchIn(body) } &&
            !financialBody
        ) {
            return FilterResult.Reject
        }

        if (!senderKnown && !financialBody && looksLikePersonalChat(body)) {
            return FilterResult.Reject
        }

        return if (senderKnown || financialBody) {
            FilterResult.Accept(message)
        } else {
            FilterResult.Reject
        }
    }

    private fun looksLikePersonalChat(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.length > 160) return false
        return !AMOUNT_HINT.containsMatchIn(trimmed)
    }

    companion object {
        private val AMOUNT_HINT = Regex("""(?i)(?:rs\.?|inr|₹)\s*[0-9]|[0-9]+\.[0-9]{2}""")
    }
}
