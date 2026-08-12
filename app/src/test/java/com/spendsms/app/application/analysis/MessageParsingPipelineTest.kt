package com.spendsms.app.application.analysis

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.port.sms.EphemeralSmsMessage
import com.spendsms.app.data.parser.ParserRulesCodec
import com.spendsms.app.domain.filtering.DefaultFinancialMessageFilter
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.parsing.ConfidencePolicy
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.DeclarativeTransactionParser
import com.spendsms.app.domain.parsing.ParseResult
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test

class MessageParsingPipelineTest {

    private lateinit var rules: DeclarativeParserRules
    private lateinit var pipeline: MessageParsingPipeline

    @Before
    fun setUp() {
        val codec = ParserRulesCodec(
            Json {
                ignoreUnknownKeys = false
                encodeDefaults = true
                explicitNulls = false
            },
        )
        rules = codec.decodeRules(
            checkNotNull(
                javaClass.classLoader!!.getResourceAsStream("parser/regression_rules.json"),
            ).bufferedReader().readText(),
        )
        pipeline = MessageParsingPipeline(
            messageFilter = DefaultFinancialMessageFilter(),
            parser = DeclarativeTransactionParser(ConfidencePolicy.Phase0),
        )
    }

    @Test
    fun process_rejectsOtpBeforeParse() {
        val result = pipeline.process(
            ephemeral("EX-BANK", "Your OTP is 999999"),
            rules,
        )
        assertThat(result).isEqualTo(MessagePipelineResult.Rejected)
    }

    @Test
    fun process_parsesSuccessfulDebit() {
        val result = pipeline.process(
            ephemeral(
                "VM-EX-BANK",
                "Rs.42.00 debited at TEA_STALL on 11-08-2026",
            ),
            rules,
        )
        assertThat(result).isInstanceOf(MessagePipelineResult.Parsed::class.java)
        val candidate = (result as MessagePipelineResult.Parsed).candidate
        assertThat(candidate.direction).isEqualTo(TransactionDirection.DEBIT)
        assertThat(candidate.amount.amountMinorUnits).isEqualTo(4_200L)
        assertThat(candidate.parserVersion.value).isEqualTo("regression-2026.08.12.0")
    }

    @Test
    fun process_returnsParseFailed_forUnsupportedFinancialNoise() {
        val result = pipeline.process(
            ephemeral("EX-BANK", "Rs.10 is your available balance reminder only"),
            rules,
        )
        assertThat(result).isInstanceOf(MessagePipelineResult.ParseFailed::class.java)
        val failed = (result as MessagePipelineResult.ParseFailed).parseResult
        assertThat(failed).isInstanceOf(ParseResult.Unsupported::class.java)
    }

    @Test
    fun process_returnsAmbiguous_forConflictingMatches() {
        val result = pipeline.process(
            ephemeral(
                "AMBIG",
                "Rs.100.00 debited for ALPHA and later Rs.200.00 debited for BETA today",
            ),
            rules,
        )
        assertThat(result).isInstanceOf(MessagePipelineResult.ParseFailed::class.java)
        assertThat((result as MessagePipelineResult.ParseFailed).parseResult)
            .isInstanceOf(ParseResult.Ambiguous::class.java)
    }

    private fun ephemeral(sender: String, body: String): EphemeralSmsMessage =
        EphemeralSmsMessage(
            sourceMessageId = "sms-1",
            sender = sender,
            receivedAt = EpochMillis.of(1_700_000_000_000L),
            body = body,
        )
}
