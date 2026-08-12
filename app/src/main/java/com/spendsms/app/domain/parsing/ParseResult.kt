package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.TransactionCandidate

/**
 * Outcome of declarative rule evaluation for one accepted SMS.
 *
 * Results must never embed raw SMS body text.
 */
sealed interface ParseResult {
    data class Success(val candidate: TransactionCandidate) : ParseResult

    data class Unsupported(
        val reason: ParseFailureReason,
        val detail: String = reason.name,
    ) : ParseResult

    data class Ambiguous(
        val detail: String,
    ) : ParseResult

    data class Malformed(
        val detail: String,
    ) : ParseResult
}

enum class ParseFailureReason {
    NO_SENDER_OR_PATTERN_MATCH,
    REQUIRED_FIELD_MISSING,
    LOW_CONFIDENCE,
    RULE_RUNTIME_ERROR,
    UNSUPPORTED_TEMPLATE,
}

/**
 * Converts an accepted financial SMS into a [TransactionCandidate] using
 * declarative parser rules (Prompt 6) — never executable rule code.
 */
interface TransactionParser {
    fun parse(
        message: com.spendsms.app.domain.sms.RawSmsMessage,
        rules: DeclarativeParserRules,
    ): ParseResult
}
