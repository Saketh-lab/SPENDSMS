package com.spendsms.app.domain.deduplication

import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.semantics.ClassifiedTransaction
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic fingerprint over approved normalized fields only (Step-2/3).
 *
 * Never includes raw SMS body or sender content.
 *
 * When a reference hash is present, time and institution are omitted so
 * multiple alerts for one payment (bank + UPI) collapse to one key.
 * Without a reference, a 5-minute time bucket plus institution/merchant/
 * masked account distinguish legitimate repeats.
 */
@Singleton
class TransactionFingerprintFactory @Inject constructor(
    private val policy: DuplicatePolicy,
) {

    fun fingerprint(classified: ClassifiedTransaction): TransactionFingerprint {
        val candidate = classified.candidate
        val amount = candidate.amount
        val merchantKey = classified.merchant?.key?.value.orEmpty()
        val reference = fold(candidate.referenceHash)
        val material = if (reference.isNotEmpty()) {
            listOf(
                SCHEMA,
                amount.amountMinorUnits.toString(),
                amount.currency.code,
                classified.direction.name,
                reference,
            ).joinToString(SEPARATOR)
        } else {
            listOf(
                SCHEMA,
                fold(candidate.institution),
                amount.amountMinorUnits.toString(),
                amount.currency.code,
                classified.direction.name,
                merchantKey,
                fold(candidate.maskedAccount),
                policy.bucket(candidate.transactionTimestamp.toEpochMillis).toString(),
            ).joinToString(SEPARATOR)
        }
        return TransactionFingerprint.of(sha256Hex(material))
    }

    companion object {
        const val SCHEMA: String = "v1"
        private const val SEPARATOR: String = "|"

        internal fun fold(value: String?): String =
            value?.trim()?.lowercase().orEmpty()

        internal fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { b -> "%02x".format(b) }
        }
    }
}
