package com.spendsms.app.domain.subscriptions

import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.SpendingRole
import com.spendsms.app.domain.semantics.TransactionSemantics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Deterministic recurring-pattern detector (Step-3 Subscription Detector).
 *
 * Machine output is always [SubscriptionStatus.SUSPECTED]. Persistence/merge
 * policy for dismissed/confirmed rows lives in the application service.
 */
@Singleton
class SubscriptionDetector @Inject constructor(
    private val semantics: TransactionSemantics,
    private val policy: SubscriptionDetectionPolicy,
) {

    fun detect(
        transactions: List<Transaction>,
        excludedTransactionIds: Set<TransactionId> = emptySet(),
        now: EpochMillis,
        idFactory: (MerchantKey) -> SubscriptionId,
    ): List<DetectedSubscription> {
        val eligible = transactions.filter { tx ->
            tx.id !in excludedTransactionIds &&
                tx.merchant != null &&
                tx.duplicateStatus != DuplicateStatus.CONFIRMED &&
                isQualifyingExpense(tx)
        }
        return eligible
            .groupBy { it.merchant!!.key }
            .mapNotNull { (merchantKey, rows) ->
                evaluateGroup(
                    merchantKey = merchantKey,
                    rows = rows.sortedBy { it.timestamp.toEpochMillis },
                    now = now,
                    idFactory = idFactory,
                )
            }
            .sortedByDescending { it.subscription.confidence.value }
    }

    private fun isQualifyingExpense(tx: Transaction): Boolean {
        val spending = semantics.resolve(
            parsedDirection = tx.direction,
            merchant = tx.merchant,
            userTransferStatus = tx.transferStatus,
        ).spending
        if (spending.role != SpendingRole.GROSS_EXPENSE) return false
        if (tx.transferStatus != TransferStatus.NONE) return false
        return tx.direction == TransactionDirection.DEBIT
    }

    private fun evaluateGroup(
        merchantKey: MerchantKey,
        rows: List<Transaction>,
        now: EpochMillis,
        idFactory: (MerchantKey) -> SubscriptionId,
    ): DetectedSubscription? {
        if (rows.size < policy.minimumOccurrences) return null
        val currency = rows.first().amount.currency
        if (rows.any { it.amount.currency != currency }) return null
        if (!amountsSimilar(rows.map { it.amount.amountMinorUnits })) return null

        val intervals = rows.zipWithNext { a, b ->
            (b.timestamp.toEpochMillis - a.timestamp.toEpochMillis).toDouble() /
                SubscriptionDetectionPolicy.DAY_MILLIS
        }
        if (intervals.isEmpty()) return null
        val medianInterval = median(intervals)
        val frequency = policy.classifyFrequency(medianInterval) ?: return null

        val amount = Money.ofMinorUnits(medianLong(rows.map { it.amount.amountMinorUnits }), currency)
        val lastPayment = rows.last().timestamp
        val intervalMs = (policy.intervalDays(frequency) * SubscriptionDetectionPolicy.DAY_MILLIS).toLong()
        val estimatedNext = EpochMillis.of(lastPayment.toEpochMillis + intervalMs)
        val inactiveCutoff = lastPayment.toEpochMillis +
            (policy.inactiveIntervalMultiples * intervalMs).toLong()
        val status = if (now.toEpochMillis > inactiveCutoff) {
            SubscriptionStatus.POSSIBLY_INACTIVE
        } else {
            SubscriptionStatus.SUSPECTED
        }

        val confidence = confidence(
            count = rows.size,
            amountSpread = amountSpreadRatio(rows.map { it.amount.amountMinorUnits }),
            intervalSpread = intervalSpreadRatio(intervals, policy.intervalDays(frequency)),
        )

        val display = rows.last().merchant?.displayName
        return DetectedSubscription(
            subscription = Subscription(
                id = idFactory(merchantKey),
                merchantKey = merchantKey,
                merchantDisplayName = display,
                frequency = frequency,
                estimatedAmount = amount,
                currency = currency,
                lastPaymentDate = lastPayment,
                estimatedNextDate = estimatedNext,
                confidence = confidence,
                status = status,
                evidenceTransactionIds = rows.map { it.id },
                createdAt = now,
                updatedAt = now,
            ),
            evidence = rows,
        )
    }

    private fun amountsSimilar(amounts: List<Long>): Boolean {
        if (amounts.isEmpty()) return false
        val median = medianLong(amounts).toDouble()
        if (median <= 0.0) return false
        return amounts.all { amount ->
            abs(amount - median) / median <= policy.amountToleranceRatio
        }
    }

    private fun amountSpreadRatio(amounts: List<Long>): Double {
        val median = medianLong(amounts).toDouble()
        if (median <= 0.0) return 1.0
        val maxDev = amounts.maxOf { abs(it - median) }
        return maxDev / median
    }

    private fun intervalSpreadRatio(intervals: List<Double>, targetDays: Double): Double {
        if (targetDays <= 0.0 || intervals.isEmpty()) return 1.0
        val maxDev = intervals.maxOf { abs(it - targetDays) }
        return (maxDev / targetDays).coerceAtMost(1.0)
    }

    private fun confidence(count: Int, amountSpread: Double, intervalSpread: Double): Confidence {
        val countScore = when {
            count >= 4 -> 0.35
            count == 3 -> 0.25
            else -> 0.15
        }
        val amountScore = (0.35 * (1.0 - amountSpread.coerceIn(0.0, 1.0)))
        val intervalScore = (0.30 * (1.0 - intervalSpread.coerceIn(0.0, 1.0)))
        return Confidence.of((countScore + amountScore + intervalScore).coerceIn(0.0, 1.0))
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    private fun medianLong(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2L
        } else {
            sorted[mid]
        }
    }
}

data class DetectedSubscription(
    val subscription: Subscription,
    val evidence: List<Transaction>,
)
