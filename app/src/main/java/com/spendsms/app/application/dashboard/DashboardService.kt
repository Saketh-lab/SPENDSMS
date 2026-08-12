package com.spendsms.app.application.dashboard

import com.spendsms.app.application.port.DashboardCachePort
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.SubscriptionRepository
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.domain.dashboard.DashboardCalculator
import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.TransactionId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds dashboard summaries from Room and refreshes the optional cache.
 */
@Singleton
class DashboardService @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userCorrectionRepository: UserCorrectionRepository,
    private val scanStateRepository: ScanStateRepository,
    private val dashboardCachePort: DashboardCachePort,
    private val calculator: DashboardCalculator,
) {

    suspend fun getDashboard(
        period: AnalysisPeriod,
        useCache: Boolean = true,
        currency: CurrencyCode = CurrencyCode.INR,
    ): DashboardResult {
        if (useCache) {
            dashboardCachePort.get(period)?.let { return it }
        }
        return recomputeAndCache(period, currency)
    }

    suspend fun recomputeAndCache(
        period: AnalysisPeriod,
        currency: CurrencyCode = CurrencyCode.INR,
    ): DashboardResult {
        val result = compute(period, currency)
        runCatching { dashboardCachePort.put(result) }
        return result
    }

    suspend fun invalidateCache() {
        dashboardCachePort.invalidateAll()
    }

    suspend fun compute(
        period: AnalysisPeriod,
        currency: CurrencyCode = CurrencyCode.INR,
    ): DashboardResult {
        val transactions = transactionRepository.findInPeriod(period)
        val excluded = excludedNotATransactionIds(transactions.map { it.id })
        val subscriptions = subscriptionRepository.listAll()
        val lastAnalysis = scanStateRepository.findLatestCompleted()?.completedAt
        return calculator.calculate(
            period = period,
            transactions = transactions,
            subscriptions = subscriptions,
            excludedTransactionIds = excluded,
            lastAnalysisAt = lastAnalysis,
            currency = currency,
        )
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
