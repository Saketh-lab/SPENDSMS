package com.spendsms.app.application.subscriptions

import com.spendsms.app.application.port.SubscriptionRepository
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.subscriptions.DetectedSubscription
import com.spendsms.app.domain.subscriptions.Subscription
import com.spendsms.app.domain.subscriptions.SubscriptionDetector
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun interface SubscriptionIdFactory {
    fun next(merchantKey: MerchantKey): SubscriptionId
}

@Singleton
class UuidSubscriptionIdFactory @Inject constructor() : SubscriptionIdFactory {
    override fun next(merchantKey: MerchantKey): SubscriptionId =
        SubscriptionId.of(UUID.randomUUID().toString())
}

/**
 * Runs [SubscriptionDetector] and merges results into Room without resurrecting
 * dismissed suggestions unless evidence is materially new.
 */
@Singleton
class SubscriptionDetectionService @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userCorrectionRepository: UserCorrectionRepository,
    private val detector: SubscriptionDetector,
    private val idFactory: SubscriptionIdFactory,
) {

    suspend fun detectAndPersist(
        period: AnalysisPeriod? = null,
        now: EpochMillis,
    ): List<Subscription> {
        val transactions = if (period == null) {
            transactionRepository.query(
                com.spendsms.app.application.port.TransactionQuery(limit = null),
            )
        } else {
            transactionRepository.findInPeriod(period)
        }
        val excluded = excludedNotATransactionIds(transactions.map { it.id })
        val detected = detector.detect(
            transactions = transactions,
            excludedTransactionIds = excluded,
            now = now,
            idFactory = { key -> idFactory.next(key) },
        )
        val existing = subscriptionRepository.listAll().associateBy { it.merchantKey }
        val persisted = detected.mapNotNull { candidate ->
            mergeAndPersist(candidate, existing[candidate.subscription.merchantKey], now)
        }
        return persisted
    }

    suspend fun updateStatus(
        id: SubscriptionId,
        status: SubscriptionStatus,
    ): Subscription? = subscriptionRepository.updateStatus(id, status)

    private suspend fun mergeAndPersist(
        detected: DetectedSubscription,
        existing: Subscription?,
        now: EpochMillis,
    ): Subscription? {
        val incoming = detected.subscription
        if (existing == null) {
            return subscriptionRepository.upsert(incoming)
        }
        if (existing.status == SubscriptionStatus.DISMISSED) {
            if (!hasMateriallyNewEvidence(existing, incoming)) {
                return existing
            }
            return subscriptionRepository.upsert(
                incoming.copy(
                    id = existing.id,
                    status = SubscriptionStatus.SUSPECTED,
                    createdAt = existing.createdAt,
                    updatedAt = now,
                ),
            )
        }
        val status = when (existing.status) {
            SubscriptionStatus.CONFIRMED -> SubscriptionStatus.CONFIRMED
            SubscriptionStatus.POSSIBLY_INACTIVE -> incoming.status
            SubscriptionStatus.SUSPECTED -> incoming.status
            SubscriptionStatus.DISMISSED -> SubscriptionStatus.SUSPECTED
        }
        return subscriptionRepository.upsert(
            incoming.copy(
                id = existing.id,
                status = status,
                createdAt = existing.createdAt,
                updatedAt = now,
            ),
        )
    }

    private fun hasMateriallyNewEvidence(existing: Subscription, incoming: Subscription): Boolean {
        val old = existing.evidenceTransactionIds.toSet()
        val newIds = incoming.evidenceTransactionIds.toSet()
        return newIds.any { it !in old }
    }

    private suspend fun excludedNotATransactionIds(
        transactionIds: List<TransactionId>,
    ): Set<TransactionId> {
        val excluded = mutableSetOf<TransactionId>()
        for (id in transactionIds) {
            val corrections = userCorrectionRepository.findByTransactionId(id)
            if (corrections.any { it.field == CorrectionField.NOT_A_TRANSACTION }) {
                excluded += id
            }
        }
        return excluded
    }
}
