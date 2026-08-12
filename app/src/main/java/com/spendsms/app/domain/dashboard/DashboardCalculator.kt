package com.spendsms.app.domain.dashboard

import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.semantics.SpendingRole
import com.spendsms.app.domain.semantics.TransactionSemantics
import com.spendsms.app.domain.subscriptions.Subscription
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure dashboard aggregates from effective local transactions (Step-3 Dashboard Calculator).
 *
 * Reuses Prompt-8 [TransactionSemantics] for inclusion/exclusion — no parallel rules.
 */
@Singleton
class DashboardCalculator @Inject constructor(
    private val semantics: TransactionSemantics,
) {

    fun calculate(
        period: AnalysisPeriod,
        transactions: List<Transaction>,
        subscriptions: List<Subscription>,
        excludedTransactionIds: Set<TransactionId> = emptySet(),
        lastAnalysisAt: EpochMillis? = null,
        currency: CurrencyCode = CurrencyCode.INR,
        recentLimit: Int = DEFAULT_RECENT_LIMIT,
    ): DashboardResult {
        val inPeriod = transactions.filter { tx ->
            tx.timestamp.toEpochMillis in period.start.toEpochMillis..period.end.toEpochMillis
        }
        var gross = Money.zero(currency)
        var credits = Money.zero(currency)
        var refunds = Money.zero(currency)
        val categoryBuckets = linkedMapOf<String, MutableMoneyCount>()
        val monthlyBuckets = linkedMapOf<String, Long>()
        val merchantBuckets = linkedMapOf<String, MutableMerchantBucket>()

        for (tx in inPeriod) {
            val role = spendingRole(tx, excludedTransactionIds)
            val amount = alignCurrency(tx.amount, currency) ?: continue
            when (role) {
                SpendingRole.GROSS_EXPENSE -> {
                    gross += amount
                    addCategory(categoryBuckets, tx, amount)
                    addMonth(monthlyBuckets, tx, amount)
                    addMerchant(merchantBuckets, tx, amount)
                }
                SpendingRole.CREDIT -> credits += amount
                SpendingRole.REFUND -> refunds += amount
                SpendingRole.TRANSFER,
                SpendingRole.EXCLUDED,
                -> Unit
            }
        }

        val net = gross.minusFlooringAtZero(refunds)
        val subscriptionTotals = subscriptionTotals(subscriptions, currency)
        val suspected = subscriptions
            .filter {
                it.status == SubscriptionStatus.SUSPECTED ||
                    it.status == SubscriptionStatus.CONFIRMED ||
                    it.status == SubscriptionStatus.POSSIBLY_INACTIVE
            }
            .sortedByDescending { it.confidence.value }
            .map {
                SubscriptionSummary(
                    id = it.id,
                    merchantDisplayName = it.merchantDisplayName,
                    status = it.status,
                    estimatedAmount = it.estimatedAmount,
                    evidenceCount = it.evidenceTransactionIds.size,
                )
            }

        return DashboardResult(
            period = period,
            grossSpending = gross,
            netSpending = net,
            credits = credits,
            refunds = refunds,
            transactionCount = inPeriod.size,
            categoryTotals = categoryBuckets.entries
                .map { (id, bucket) ->
                    CategoryTotal(
                        categoryId = com.spendsms.app.domain.model.CategoryId.of(id),
                        total = Money.ofMinorUnits(bucket.minor, currency),
                        transactionCount = bucket.count,
                    )
                }
                .sortedByDescending { it.total.amountMinorUnits },
            monthlyTotals = monthlyBuckets.entries
                .sortedBy { it.key }
                .map { (ym, minor) ->
                    MonthlyTotal(
                        yearMonth = YearMonthKey.of(ym),
                        total = Money.ofMinorUnits(minor, currency),
                    )
                },
            merchantTotals = merchantBuckets.values
                .map {
                    MerchantTotal(
                        merchantKey = it.key,
                        displayName = it.displayName,
                        total = Money.ofMinorUnits(it.minor, currency),
                        transactionCount = it.count,
                    )
                }
                .sortedByDescending { it.total.amountMinorUnits },
            subscriptionTotals = subscriptionTotals,
            recentTransactions = inPeriod
                .sortedByDescending { it.timestamp.toEpochMillis }
                .take(recentLimit.coerceAtLeast(0)),
            suspectedSubscriptions = suspected,
            lastAnalysisAt = lastAnalysisAt,
        )
    }

    fun spendingRole(
        transaction: Transaction,
        excludedTransactionIds: Set<TransactionId> = emptySet(),
    ): SpendingRole {
        if (transaction.id in excludedTransactionIds) return SpendingRole.EXCLUDED
        if (transaction.duplicateStatus == DuplicateStatus.CONFIRMED) return SpendingRole.EXCLUDED
        return semantics.resolve(
            parsedDirection = transaction.direction,
            merchant = transaction.merchant,
            userTransferStatus = transaction.transferStatus,
        ).spending.role
    }

    private fun subscriptionTotals(
        subscriptions: List<Subscription>,
        currency: CurrencyCode,
    ): SubscriptionTotals {
        val active = subscriptions.filter {
            it.status == SubscriptionStatus.SUSPECTED ||
                it.status == SubscriptionStatus.CONFIRMED
        }
        var monthly = Money.zero(currency)
        for (sub in active) {
            val amount = sub.estimatedAmount ?: continue
            val aligned = alignCurrency(amount, currency) ?: continue
            monthly += toMonthly(aligned, sub.frequency)
        }
        return SubscriptionTotals(
            estimatedMonthly = monthly,
            estimatedAnnual = Money.ofMinorUnits(monthly.amountMinorUnits * 12L, currency),
            activeOrSuspectedCount = active.size,
        )
    }

    private fun toMonthly(
        amount: Money,
        frequency: com.spendsms.app.domain.model.SubscriptionFrequency,
    ): Money = when (frequency) {
        com.spendsms.app.domain.model.SubscriptionFrequency.WEEKLY ->
            Money.ofMinorUnits(amount.amountMinorUnits * 52L / 12L, amount.currency)
        com.spendsms.app.domain.model.SubscriptionFrequency.MONTHLY -> amount
        com.spendsms.app.domain.model.SubscriptionFrequency.YEARLY ->
            Money.ofMinorUnits(amount.amountMinorUnits / 12L, amount.currency)
        com.spendsms.app.domain.model.SubscriptionFrequency.UNKNOWN -> amount
    }

    private fun alignCurrency(amount: Money, currency: CurrencyCode): Money? =
        if (amount.currency == currency) amount else null

    private fun addCategory(
        buckets: MutableMap<String, MutableMoneyCount>,
        tx: Transaction,
        amount: Money,
    ) {
        val key = tx.categoryId.value
        val bucket = buckets.getOrPut(key) { MutableMoneyCount() }
        bucket.minor += amount.amountMinorUnits
        bucket.count += 1
    }

    private fun addMonth(
        buckets: MutableMap<String, Long>,
        tx: Transaction,
        amount: Money,
    ) {
        val key = yearMonthUtc(tx.timestamp)
        buckets[key] = (buckets[key] ?: 0L) + amount.amountMinorUnits
    }

    private fun addMerchant(
        buckets: MutableMap<String, MutableMerchantBucket>,
        tx: Transaction,
        amount: Money,
    ) {
        val merchant = tx.merchant ?: return
        val bucket = buckets.getOrPut(merchant.key.value) {
            MutableMerchantBucket(merchant.key, merchant.displayName)
        }
        bucket.minor += amount.amountMinorUnits
        bucket.count += 1
    }

    private fun yearMonthUtc(timestamp: EpochMillis): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timestamp.toEpochMillis
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return "%04d-%02d".format(year, month)
    }

    private class MutableMoneyCount(
        var minor: Long = 0L,
        var count: Int = 0,
    )

    private class MutableMerchantBucket(
        val key: com.spendsms.app.domain.model.MerchantKey,
        val displayName: String,
        var minor: Long = 0L,
        var count: Int = 0,
    )

    companion object {
        const val DEFAULT_RECENT_LIMIT: Int = 10
    }
}
