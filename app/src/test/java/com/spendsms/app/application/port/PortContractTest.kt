package com.spendsms.app.application.port

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.port.config.AppRemoteConfig
import com.spendsms.app.application.port.sms.EphemeralSmsMessage
import com.spendsms.app.application.port.sms.SmsAccessException
import com.spendsms.app.application.port.sms.SmsBatch
import com.spendsms.app.application.port.sms.SmsBatchQuery
import com.spendsms.app.application.port.sms.SmsMessageSource
import com.spendsms.app.application.port.support.RedactedUnsupportedFormatSubmission
import com.spendsms.app.application.port.support.UnsupportedFormatReason
import com.spendsms.app.application.port.telemetry.TelemetryCountBucket
import com.spendsms.app.application.port.telemetry.TelemetryDurationBucket
import com.spendsms.app.application.port.telemetry.TelemetryEvent
import com.spendsms.app.application.port.telemetry.TelemetryEventType
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.Transaction
import org.junit.Assert.assertThrows
import org.junit.Test

class PortContractTest {

    @Test
    fun ephemeralSms_allowsBody_butTransactionHasNoBodyField() {
        val sms = EphemeralSmsMessage(
            sourceMessageId = "1",
            sender = "EX-BANK",
            receivedAt = EpochMillis.of(1L),
            body = "Debited INR 100",
        )
        assertThat(sms.body).isEqualTo("Debited INR 100")
        assertThat(sms.toString()).doesNotContain("Debited INR 100")
        assertThat(sms.toString()).doesNotContain("EX-BANK")
        assertThat(sms.toString()).contains("body=***")

        val transactionFields = Transaction::class.java.declaredFields.map { it.name }
        assertThat(transactionFields).doesNotContain("body")
        assertThat(transactionFields).doesNotContain("smsBody")
    }

    @Test
    fun smsBatchQuery_rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            SmsBatchQuery(
                period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L)),
                limit = 0,
            )
        }
    }

    @Test
    fun telemetryWireValues_matchStep4AllowList() {
        assertThat(TelemetryEventType.SCAN_COMPLETED.wireValue()).isEqualTo("scan_completed")
        assertThat(TelemetryDurationBucket.S_30_60.wireValue()).isEqualTo("30S_60S")
        assertThat(TelemetryCountBucket.FROM_51_TO_200.wireValue()).isEqualTo("51_200")
    }

    @Test
    fun telemetryEvent_rejectsInvalidErrorCode() {
        assertThrows(IllegalArgumentException::class.java) {
            TelemetryEvent(
                eventId = "e1",
                occurredAt = EpochMillis.of(1L),
                eventType = TelemetryEventType.APP_ERROR,
                errorCode = "bad-code",
            )
        }
    }

    @Test
    fun supportSubmission_requiresPreviewConfirmed() {
        assertThrows(IllegalArgumentException::class.java) {
            RedactedUnsupportedFormatSubmission(
                submissionId = "s1",
                idempotencyKey = "k1",
                consentedAt = EpochMillis.of(1L),
                previewConfirmed = false,
                redactionVersion = "1",
                reason = UnsupportedFormatReason.NO_RULE_MATCH,
                redactedTemplate = "Debited [AMOUNT]",
                parserVersion = null,
            )
        }
    }

    @Test
    fun supportSubmission_rejectsUnredactedSmsTemplate() {
        assertThrows(IllegalArgumentException::class.java) {
            RedactedUnsupportedFormatSubmission(
                submissionId = "s1",
                idempotencyKey = "k1",
                consentedAt = EpochMillis.of(1L),
                previewConfirmed = true,
                redactionVersion = "1",
                reason = UnsupportedFormatReason.NO_RULE_MATCH,
                redactedTemplate = "Rs.1,250.50 debited at SWIGGY from A/c XX1234",
                parserVersion = null,
            )
        }
    }

    @Test
    fun bundledRemoteConfig_isAvailableOffline() {
        val config = AppRemoteConfig.localOnlyDefaults()
        assertThat(config.supportSubmissionEnabled).isFalse()
        assertThat(config.newParserVersionEnabled).isFalse()
        assertThat(config.controlledImportEnabled).isTrue()
    }

    @Test
    fun smsMessageSource_canBeStubbedWithoutAndroidApis() = kotlinx.coroutines.runBlocking {
        val source = object : SmsMessageSource {
            override suspend fun readBatch(query: SmsBatchQuery): SmsBatch {
                if (query.afterMessageId == "deny") {
                    throw SmsAccessException.PermissionDenied()
                }
                return SmsBatch(messages = emptyList(), nextAfterMessageId = null, exhausted = true)
            }
        }
        val batch = source.readBatch(
            SmsBatchQuery(
                period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L)),
                limit = 50,
            ),
        )
        assertThat(batch.exhausted).isTrue()
    }

    @Test
    fun parserRulesDocument_rejectsBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            ParserRulesDocument.of("  ")
        }
    }
}
