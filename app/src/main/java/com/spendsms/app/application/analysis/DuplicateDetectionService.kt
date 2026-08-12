package com.spendsms.app.application.analysis

import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.domain.deduplication.DuplicateDetectionResult
import com.spendsms.app.domain.deduplication.DuplicateDetector
import com.spendsms.app.domain.deduplication.DuplicateOutcome
import com.spendsms.app.domain.deduplication.DuplicatePolicy
import com.spendsms.app.domain.deduplication.TransactionFingerprintFactory
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.semantics.ClassifiedTransaction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun interface TransactionIdFactory {
    fun next(): TransactionId
}

@Singleton
class UuidTransactionIdFactory @Inject constructor() : TransactionIdFactory {
    override fun next(): TransactionId = TransactionId.of(UUID.randomUUID().toString())
}

/**
 * Looks up existing rows and applies [DuplicateDetector] (Prompt 9).
 *
 * Exact matches reuse the stored row (repeated scans stay idempotent).
 * Near matches insert a new row with [DuplicateStatus.SUSPECTED].
 * Does not run SMS ingestion or scan batch orchestration.
 */
@Singleton
class DuplicateDetectionService @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val detector: DuplicateDetector,
    private val fingerprintFactory: TransactionFingerprintFactory,
    private val policy: DuplicatePolicy,
    private val userCorrectionRepository: UserCorrectionRepository,
    private val idFactory: TransactionIdFactory,
) {

    suspend fun evaluate(classified: ClassifiedTransaction): DuplicateDetectionResult {
        val fingerprint = fingerprintFactory.fingerprint(classified)
        val bySource = transactionRepository.findBySourceMessageHash(
            classified.candidate.sourceMessageHash,
        )
        val byFingerprint = transactionRepository.findByFingerprint(fingerprint)
        val nearby = transactionRepository.findNear(
            amount = classified.amount,
            around = classified.candidate.transactionTimestamp,
            windowMillis = policy.nearWindowMillis,
        )
        val override = classified.merchant?.key?.let { key ->
            userCorrectionRepository.findFutureRules(key, CorrectionField.DUPLICATE_STATUS)
                .filter { it.applyToFuture }
                .maxByOrNull { it.updatedAt.toEpochMillis }
                ?.newValue
                ?.let { raw -> runCatching { DuplicateStatus.valueOf(raw.trim().uppercase()) }.getOrNull() }
        }
        return detector.evaluate(
            classified = classified,
            existingBySourceHash = bySource,
            existingByFingerprint = byFingerprint,
            nearby = nearby,
            userDuplicateOverride = override,
        )
    }

    /**
     * Idempotent persist: exact matches return the existing row without inserting.
     */
    suspend fun resolve(
        classified: ClassifiedTransaction,
        now: EpochMillis,
    ): Transaction {
        val result = evaluate(classified)
        return when (val outcome = result.outcome) {
            is DuplicateOutcome.ReuseExisting -> outcome.existing
            is DuplicateOutcome.InsertNearDuplicate ->
                transactionRepository.upsert(
                    toTransaction(
                        classified = classified,
                        fingerprint = result.fingerprint,
                        duplicateStatus = outcome.status,
                        possibleDuplicateOf = outcome.possibleDuplicateOf,
                        id = idFactory.next(),
                        now = now,
                    ),
                )
            DuplicateOutcome.InsertUnique ->
                transactionRepository.upsert(
                    toTransaction(
                        classified = classified,
                        fingerprint = result.fingerprint,
                        duplicateStatus = DuplicateStatus.NONE,
                        possibleDuplicateOf = null,
                        id = idFactory.next(),
                        now = now,
                    ),
                )
        }
    }

    companion object {
        fun toTransaction(
            classified: ClassifiedTransaction,
            fingerprint: com.spendsms.app.domain.model.TransactionFingerprint,
            duplicateStatus: DuplicateStatus,
            possibleDuplicateOf: TransactionId?,
            id: TransactionId,
            now: EpochMillis,
        ): Transaction = Transaction(
            id = id,
            sourceMessageHash = classified.candidate.sourceMessageHash,
            fingerprint = fingerprint,
            timestamp = classified.candidate.transactionTimestamp,
            amount = classified.amount,
            merchant = classified.merchant,
            institution = classified.candidate.institution,
            maskedAccount = classified.candidate.maskedAccount,
            referenceHash = classified.candidate.referenceHash,
            direction = classified.direction,
            paymentMethod = classified.candidate.paymentMethod,
            categoryId = classified.categoryId,
            confidence = classified.confidence,
            parserVersion = classified.parserVersion,
            duplicateStatus = duplicateStatus,
            possibleDuplicateOf = possibleDuplicateOf,
            transferStatus = classified.transferStatus,
            isUserConfirmed = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
