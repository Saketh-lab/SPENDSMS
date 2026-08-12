package com.spendsms.app.domain.dashboard

import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionId

/**
 * Dashboard aggregates produced exclusively from effective local transactions
 * (Step-3 Dashboard Calculator outputs). Calculation services are deferred.
 */
data class DashboardResult(
    val period: AnalysisPeriod,
    val grossSpending: Money,
    val netSpending: Money,
    val credits: Money,
    val refunds: Money,
    val transactionCount: Int,
    val categoryTotals: List<CategoryTotal>,
    val monthlyTotals: List<MonthlyTotal>,
    val merchantTotals: List<MerchantTotal>,
    val subscriptionTotals: SubscriptionTotals,
    val recentTransactions: List<Transaction>,
    val suspectedSubscriptions: List<SubscriptionSummary>,
    val lastAnalysisAt: EpochMillis?,
) {
    init {
        require(transactionCount >= 0) { "transactionCount must be >= 0" }
        require(grossSpending.currency == netSpending.currency) {
            "grossSpending and netSpending currencies must match"
        }
        require(credits.currency == grossSpending.currency) {
            "credits currency must match grossSpending currency"
        }
        require(refunds.currency == grossSpending.currency) {
            "refunds currency must match grossSpending currency"
        }
        require(subscriptionTotals.estimatedMonthly.currency == grossSpending.currency) {
            "subscriptionTotals currency must match dashboard currency"
        }
    }
}

data class CategoryTotal(
    val categoryId: CategoryId,
    val total: Money,
    val transactionCount: Int,
) {
    init {
        require(transactionCount >= 0) { "transactionCount must be >= 0" }
    }
}

/**
 * Calendar month key as `YYYY-MM` for trend rows.
 * Kept as a string value object to avoid Android/java.time coupling in Phase-0.
 */
@JvmInline
value class YearMonthKey private constructor(val value: String) {
    init {
        require(PATTERN.matches(value)) {
            "YearMonthKey must match YYYY-MM, was '$value'"
        }
    }

    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

        fun of(value: String): YearMonthKey = YearMonthKey(value)
    }
}

data class MonthlyTotal(
    val yearMonth: YearMonthKey,
    val total: Money,
)

data class MerchantTotal(
    val merchantKey: MerchantKey,
    val displayName: String,
    val total: Money,
    val transactionCount: Int,
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(transactionCount >= 0) { "transactionCount must be >= 0" }
    }
}

data class SubscriptionTotals(
    val estimatedMonthly: Money,
    val estimatedAnnual: Money,
    val activeOrSuspectedCount: Int,
) {
    init {
        require(estimatedMonthly.currency == estimatedAnnual.currency) {
            "subscription monthly/annual currencies must match"
        }
        require(activeOrSuspectedCount >= 0) {
            "activeOrSuspectedCount must be >= 0"
        }
    }
}

data class SubscriptionSummary(
    val id: SubscriptionId,
    val merchantDisplayName: String?,
    val status: SubscriptionStatus,
    val estimatedAmount: Money?,
    val evidenceCount: Int,
) {
    init {
        require(evidenceCount >= 0) { "evidenceCount must be >= 0" }
    }
}

/**
 * Lightweight reference used when a dashboard needs only an id list without
 * embedding full [Transaction] graphs. Optional companion to recentTransactions.
 */
data class TransactionRef(
    val id: TransactionId,
)
