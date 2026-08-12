package com.spendsms.app.application.analysis

import com.spendsms.app.application.port.sms.EphemeralSmsMessage
import com.spendsms.app.domain.filtering.FilterResult
import com.spendsms.app.domain.filtering.FinancialMessageFilter
import com.spendsms.app.domain.model.TransactionCandidate
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.ParseResult
import com.spendsms.app.domain.parsing.TransactionParser
import com.spendsms.app.domain.sms.RawSmsMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local filter + declarative parse pipeline (Prompt 7).
 *
 * Does not read the SMS provider, persist transactions, or download rules.
 */
@Singleton
class MessageParsingPipeline @Inject constructor(
    private val messageFilter: FinancialMessageFilter,
    private val parser: TransactionParser,
) {

    fun filter(
        message: EphemeralSmsMessage,
        rules: DeclarativeParserRules,
    ): FilterResult = messageFilter.filter(message.toRaw(), rules.allSenderPatterns())

    fun parse(
        message: EphemeralSmsMessage,
        rules: DeclarativeParserRules,
    ): ParseResult = parser.parse(message.toRaw(), rules)

    /**
     * Filter then parse. Rejected messages never reach the parser.
     */
    fun process(
        message: EphemeralSmsMessage,
        rules: DeclarativeParserRules,
    ): MessagePipelineResult {
        return when (val filtered = filter(message, rules)) {
            FilterResult.Reject -> MessagePipelineResult.Rejected
            is FilterResult.Accept -> when (val parsed = parser.parse(filtered.message, rules)) {
                is ParseResult.Success -> MessagePipelineResult.Parsed(parsed.candidate)
                is ParseResult.Unsupported ->
                    MessagePipelineResult.ParseFailed(parsed)
                is ParseResult.Ambiguous ->
                    MessagePipelineResult.ParseFailed(parsed)
                is ParseResult.Malformed ->
                    MessagePipelineResult.ParseFailed(parsed)
            }
        }
    }
}

sealed interface MessagePipelineResult {
    data object Rejected : MessagePipelineResult

    data class Parsed(val candidate: TransactionCandidate) : MessagePipelineResult

    data class ParseFailed(val parseResult: ParseResult) : MessagePipelineResult
}

fun EphemeralSmsMessage.toRaw(): RawSmsMessage =
    RawSmsMessage(
        sourceMessageId = sourceMessageId,
        sender = sender,
        receivedAt = receivedAt,
        body = body,
    )

fun DeclarativeParserRules.allSenderPatterns(): List<String> =
    institutions.flatMap { it.senderPatterns }
