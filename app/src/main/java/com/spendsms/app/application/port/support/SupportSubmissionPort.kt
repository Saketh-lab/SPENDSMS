package com.spendsms.app.application.port.support

import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserVersion

/**
 * Reason values for unsupported-format support submissions (Step-4).
 */
enum class UnsupportedFormatReason {
    NO_RULE_MATCH,
    PARSE_FAILED,
    INCORRECT_EXTRACTION,
    OTHER,
    ;

    fun wireValue(): String = when (this) {
        NO_RULE_MATCH -> "no_rule_match"
        PARSE_FAILED -> "parse_failed"
        INCORRECT_EXTRACTION -> "incorrect_extraction"
        OTHER -> "other"
    }
}

/**
 * User-approved redacted template only — never raw SMS.
 */
data class RedactedUnsupportedFormatSubmission(
    val submissionId: String,
    val idempotencyKey: String,
    val consentedAt: EpochMillis,
    val previewConfirmed: Boolean,
    val redactionVersion: String,
    val reason: UnsupportedFormatReason,
    val redactedTemplate: String,
    val parserVersion: ParserVersion?,
) {
    init {
        require(submissionId.isNotBlank()) { "submissionId must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(previewConfirmed) {
            "previewConfirmed must be true before submission"
        }
        require(redactionVersion.isNotBlank()) { "redactionVersion must not be blank" }
        require(redactedTemplate.isNotBlank()) { "redactedTemplate must not be blank" }
        require(redactedTemplate.length <= 4096) {
            "redactedTemplate must be <= 4096 characters"
        }
    }
}

data class SupportSubmissionResult(
    val submissionId: String,
    val accepted: Boolean,
    val deletionScheduledAt: EpochMillis?,
)

/**
 * Explicitly consented redacted unsupported-format submission port (Step-4).
 */
interface SupportSubmissionPort {

    suspend fun submit(
        submission: RedactedUnsupportedFormatSubmission,
    ): SupportSubmissionResult
}
