package com.spendsms.app.domain.deduplication

import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.semantics.ClassifiedTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Classifies exact vs near duplicates without destructive merging of ambiguous
 * matches (Step-3 Duplicate Detector).
 */
@Singleton
class DuplicateDetector @Inject constructor(
    private val fingerprintFactory: TransactionFingerprintFactory,
    private val policy: DuplicatePolicy = DuplicatePolicy.Phase0,
) {

    fun evaluate(
        classified: ClassifiedTransaction,
        existingBySourceHash: Transaction?,
        existingByFingerprint: Transaction?,
        nearby: List<Transaction>,
        userDuplicateOverride: DuplicateStatus? = null,
    ): DuplicateDetectionResult {
        val fingerprint = fingerprintFactory.fingerprint(classified)

        existingBySourceHash?.let { existing ->
            return DuplicateDetectionResult(
                fingerprint = fingerprint,
                outcome = DuplicateOutcome.ReuseExisting(existing),
            )
        }

        existingByFingerprint?.let { existing ->
            return DuplicateDetectionResult(
                fingerprint = fingerprint,
                outcome = DuplicateOutcome.ReuseExisting(existing),
            )
        }

        if (userDuplicateOverride == DuplicateStatus.NONE) {
            return DuplicateDetectionResult(
                fingerprint = fingerprint,
                outcome = DuplicateOutcome.InsertUnique,
            )
        }

        val near = nearestPlausible(classified, fingerprint, nearby)
        if (near != null) {
            val status = userDuplicateOverride ?: DuplicateStatus.SUSPECTED
            return DuplicateDetectionResult(
                fingerprint = fingerprint,
                outcome = DuplicateOutcome.InsertNearDuplicate(
                    possibleDuplicateOf = near.id,
                    status = status,
                ),
            )
        }

        return DuplicateDetectionResult(
            fingerprint = fingerprint,
            outcome = DuplicateOutcome.InsertUnique,
        )
    }

    private fun nearestPlausible(
        classified: ClassifiedTransaction,
        fingerprint: TransactionFingerprint,
        nearby: List<Transaction>,
    ): Transaction? {
        val incomingTime = classified.candidate.transactionTimestamp.toEpochMillis
        val incomingMerchant = classified.merchant?.key
        val incomingRef = classified.candidate.referenceHash
        val incomingMasked = foldAccount(classified.candidate.maskedAccount)

        return nearby
            .asSequence()
            .filter { it.fingerprint != fingerprint }
            .filter { it.direction == classified.direction }
            .filter { it.amount == classified.amount }
            .filter { abs(it.timestamp.toEpochMillis - incomingTime) <= policy.nearWindowMillis }
            .filter { it.merchant?.key == incomingMerchant }
            .filter { referencesCompatible(incomingRef, it.referenceHash) }
            .filter { maskedCompatible(incomingMasked, foldAccount(it.maskedAccount)) }
            .sortedWith(
                compareBy<Transaction> {
                    abs(it.timestamp.toEpochMillis - incomingTime)
                }.thenBy { it.id.value },
            )
            .firstOrNull()
    }

    private fun referencesCompatible(incoming: String?, existing: String?): Boolean {
        if (incoming.isNullOrBlank() || existing.isNullOrBlank()) return true
        return incoming == existing
    }

    private fun maskedCompatible(incoming: String, existing: String): Boolean {
        if (incoming.isEmpty() || existing.isEmpty()) return true
        return incoming == existing
    }

    private fun foldAccount(value: String?): String =
        TransactionFingerprintFactory.fold(value)
}

data class DuplicateDetectionResult(
    val fingerprint: TransactionFingerprint,
    val outcome: DuplicateOutcome,
)

sealed class DuplicateOutcome {
    /** Same SMS or same exact financial identity — do not insert another row. */
    data class ReuseExisting(val existing: Transaction) : DuplicateOutcome()

    /** Ambiguous near match — insert a new row flagged for review. */
    data class InsertNearDuplicate(
        val possibleDuplicateOf: TransactionId,
        val status: DuplicateStatus,
    ) : DuplicateOutcome()

    data object InsertUnique : DuplicateOutcome()
}
