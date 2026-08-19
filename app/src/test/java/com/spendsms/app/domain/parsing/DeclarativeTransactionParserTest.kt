package com.spendsms.app.domain.parsing

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.data.parser.ParserRulesCodec
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.sms.RawSmsMessage
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test

class DeclarativeTransactionParserTest {

    private lateinit var rules: DeclarativeParserRules
    private lateinit var parser: DeclarativeTransactionParser

    @Before
    fun setUp() {
        val codec = ParserRulesCodec(
            Json {
                ignoreUnknownKeys = false
                encodeDefaults = true
                explicitNulls = false
            },
        )
        val utf8 = checkNotNull(
            javaClass.classLoader!!.getResourceAsStream("parser/regression_rules.json"),
        ).bufferedReader().readText()
        rules = codec.decodeRules(utf8)
        parser = DeclarativeTransactionParser(ConfidencePolicy.Phase0)
    }

    @Test
    fun parsesDebitWithMerchantAndPropagatesParserVersion() {
        val message = sms(
            sender = "VM-EX-BANK",
            body = "Rs.1,250.50 debited from A/c XX1234 at SWIGGY on 10-08-2026. Avl bal Rs.5000",
        )
        val result = parser.parse(message, rules)
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val candidate = (result as ParseResult.Success).candidate
        assertThat(candidate.amount.amountMinorUnits).isEqualTo(125_050L)
        assertThat(candidate.amount.currency).isEqualTo(CurrencyCode.INR)
        assertThat(candidate.direction).isEqualTo(TransactionDirection.DEBIT)
        assertThat(candidate.merchantRaw).isEqualTo("SWIGGY")
        assertThat(candidate.institution).isEqualTo("Example Bank")
        assertThat(candidate.parserVersion).isEqualTo(rules.parserVersion)
        assertThat(candidate.transactionTimestamp.toEpochMillis).isEqualTo(
            SmsDateParser.parseOrNull("10-08-2026")!!.toEpochMillis,
        )
        assertThat(candidate.confidence.value).isAtLeast(0.60)
        assertThat(candidate.sourceMessageHash).isNotEmpty()
    }

    @Test
    fun parsesCredit() {
        val result = parser.parse(
            sms(sender = "EXBANK", body = "Rs.500.00 credited to your A/c XX99"),
            rules,
        )
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val candidate = (result as ParseResult.Success).candidate
        assertThat(candidate.direction).isEqualTo(TransactionDirection.CREDIT)
        assertThat(candidate.amount.amountMinorUnits).isEqualTo(50_000L)
        assertThat(candidate.merchantRaw).isNull()
    }

    @Test
    fun parsesRefundAndUpiPaymentMethod() {
        val result = parser.parse(
            sms(
                sender = "EX-BANK",
                body = "Rs.99.00 refunded from AMAZON to your card ending 1111",
            ),
            rules,
        )
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val candidate = (result as ParseResult.Success).candidate
        assertThat(candidate.direction).isEqualTo(TransactionDirection.REFUND)
        assertThat(candidate.merchantRaw).contains("AMAZON")
        assertThat(candidate.paymentMethod).isEqualTo(PaymentMethod.CARD)
    }

    @Test
    fun parsesUpiDebitWithReferenceHash() {
        val result = parser.parse(
            sms(
                sender = "JD-DEMOUPI",
                body = "INR 75.25 sent to coffee@oksbi via UPI. UPI Ref: 123456789012",
            ),
            rules,
        )
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val candidate = (result as ParseResult.Success).candidate
        assertThat(candidate.direction).isEqualTo(TransactionDirection.DEBIT)
        assertThat(candidate.amount.amountMinorUnits).isEqualTo(7_525L)
        assertThat(candidate.paymentMethod).isEqualTo(PaymentMethod.UPI)
        assertThat(candidate.referenceHash).isNotNull()
        assertThat(candidate.referenceHash).isEqualTo(
            ParsingHashes.referenceHash("123456789012"),
        )
        // Must not retain raw reference string on the candidate beyond the hash.
        assertThat(candidate.toString()).doesNotContain("123456789012")
    }

    @Test
    fun parsesTransfer() {
        val result = parser.parse(
            sms(
                sender = "EX-BANK",
                body = "Rs.2000.00 transferred to SAVINGS Ref:NEFT9911",
            ),
            rules,
        )
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        val candidate = (result as ParseResult.Success).candidate
        assertThat(candidate.direction).isEqualTo(TransactionDirection.TRANSFER)
        assertThat(candidate.referenceHash).isNotNull()
    }

    @Test
    fun unsupportedTemplate_whenSenderKnownButBodyUnmatched() {
        val result = parser.parse(
            sms(sender = "VM-EX-BANK", body = "Your Example Bank statement is ready to download"),
            rules,
        )
        assertThat(result).isInstanceOf(ParseResult.Unsupported::class.java)
        assertThat((result as ParseResult.Unsupported).reason)
            .isEqualTo(ParseFailureReason.UNSUPPORTED_TEMPLATE)
    }

    @Test
    fun malformedAmount_yieldsUnsupported() {
        // Matches debit skeleton poorly — amount group won't parse if weird
        val result = parser.parse(
            sms(sender = "EX-BANK", body = "Rs. debited at SWIGGY"),
            rules,
        )
        assertThat(result).isInstanceOf(ParseResult.Unsupported::class.java)
    }

    @Test
    fun ambiguous_whenTwoInstitutionsExtractConflictingValues() {
        val result = parser.parse(
            sms(
                sender = "AX-AMBIG",
                body = "Rs.100.00 debited for ALPHA and later Rs.200.00 debited for BETA today",
            ),
            rules,
        )
        assertThat(result).isInstanceOf(ParseResult.Ambiguous::class.java)
    }

    @Test
    fun neverPutsSmsBodyOnCandidate() {
        val body = "Rs.10.00 debited at SECRET_MERCHANT_XYZ"
        val result = parser.parse(sms(sender = "EX-BANK", body = body), rules)
        val candidate = (result as ParseResult.Success).candidate
        assertThat(candidate.toString()).doesNotContain(body)
        assertThat(candidate.sourceMessageHash).doesNotContain("SECRET")
    }

    @Test
    fun parse_throughputOnSyntheticInbox_staysWithinBudget() {
        val messages = (1..2_000).map { i ->
            sms(
                sender = "EX-BANK",
                body = "Rs.${10 + (i % 90)}.00 debited at SWIGGY on 10-08-2026. Ref $i",
                id = "msg-$i",
                receivedAt = 1_700_000_000_000L + i,
            )
        }
        val started = System.nanoTime()
        var success = 0
        for (message in messages) {
            val result = parser.parse(message, rules)
            if (result is ParseResult.Success) success += 1
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertThat(success).isEqualTo(2_000)
        assertThat(elapsedMs).isLessThan(8_000L)
    }

    private fun sms(
        sender: String,
        body: String,
        id: String = "msg-1",
        receivedAt: Long = 1_700_000_000_000L,
    ): RawSmsMessage = RawSmsMessage(
        sourceMessageId = id,
        sender = sender,
        receivedAt = EpochMillis.of(receivedAt),
        body = body,
    )
}
