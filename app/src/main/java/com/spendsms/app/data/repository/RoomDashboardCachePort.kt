package com.spendsms.app.data.repository

import com.spendsms.app.application.port.DashboardCachePort
import com.spendsms.app.data.room.dao.DashboardCacheDao
import com.spendsms.app.data.room.dao.TransactionDao
import com.spendsms.app.data.room.entity.DashboardCacheEntity
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.domain.dashboard.CategoryTotal
import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.domain.dashboard.MerchantTotal
import com.spendsms.app.domain.dashboard.MonthlyTotal
import com.spendsms.app.domain.dashboard.SubscriptionSummary
import com.spendsms.app.domain.dashboard.SubscriptionTotals
import com.spendsms.app.domain.dashboard.YearMonthKey
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Derived dashboard cache. Payload never contains raw SMS.
 * Recent transactions are rehydrated from the financial SoR by id.
 */
@Singleton
class RoomDashboardCachePort @Inject constructor(
    private val dashboardCacheDao: DashboardCacheDao,
    private val transactionDao: TransactionDao,
    private val json: Json,
) : DashboardCachePort {

    override suspend fun get(period: AnalysisPeriod): DashboardResult? {
        val entity = dashboardCacheDao.findByPeriod(
            periodStart = period.start.toEpochMillis,
            periodEnd = period.end.toEpochMillis,
        ) ?: return null
        val payload = runCatching {
            json.decodeFromString(DashboardCachePayload.serializer(), entity.payloadJson)
        }.getOrNull() ?: return null

        val recent = payload.recentTransactionIds.mapNotNull { id ->
            transactionDao.findById(id)?.toDomain()
        }
        val currency = CurrencyCode.of(payload.currency)
        return DashboardResult(
            period = period,
            grossSpending = Money.ofMinorUnits(payload.grossMinor, currency),
            netSpending = Money.ofMinorUnits(payload.netMinor, currency),
            credits = Money.ofMinorUnits(payload.creditsMinor, currency),
            refunds = Money.ofMinorUnits(payload.refundsMinor, currency),
            transactionCount = payload.transactionCount,
            categoryTotals = payload.categoryTotals.map {
                CategoryTotal(
                    categoryId = CategoryId.of(it.categoryId),
                    total = Money.ofMinorUnits(it.totalMinor, currency),
                    transactionCount = it.transactionCount,
                )
            },
            monthlyTotals = payload.monthlyTotals.map {
                MonthlyTotal(
                    yearMonth = YearMonthKey.of(it.yearMonth),
                    total = Money.ofMinorUnits(it.totalMinor, currency),
                )
            },
            merchantTotals = payload.merchantTotals.map {
                MerchantTotal(
                    merchantKey = MerchantKey.of(it.merchantKey),
                    displayName = it.displayName,
                    total = Money.ofMinorUnits(it.totalMinor, currency),
                    transactionCount = it.transactionCount,
                )
            },
            subscriptionTotals = SubscriptionTotals(
                estimatedMonthly = Money.ofMinorUnits(payload.subscriptionMonthlyMinor, currency),
                estimatedAnnual = Money.ofMinorUnits(payload.subscriptionAnnualMinor, currency),
                activeOrSuspectedCount = payload.subscriptionActiveCount,
            ),
            recentTransactions = recent,
            suspectedSubscriptions = payload.suspectedSubscriptions.map {
                SubscriptionSummary(
                    id = SubscriptionId.of(it.id),
                    merchantDisplayName = it.merchantDisplayName,
                    status = SubscriptionStatus.valueOf(it.status),
                    estimatedAmount = it.estimatedAmountMinor?.let { minor ->
                        Money.ofMinorUnits(minor, currency)
                    },
                    evidenceCount = it.evidenceCount,
                )
            },
            lastAnalysisAt = payload.lastAnalysisAt?.let(EpochMillis::of),
        )
    }

    override suspend fun put(result: DashboardResult) {
        val currency = result.grossSpending.currency.code
        val payload = DashboardCachePayload(
            currency = currency,
            grossMinor = result.grossSpending.amountMinorUnits,
            netMinor = result.netSpending.amountMinorUnits,
            creditsMinor = result.credits.amountMinorUnits,
            refundsMinor = result.refunds.amountMinorUnits,
            transactionCount = result.transactionCount,
            categoryTotals = result.categoryTotals.map {
                CachedCategoryTotal(
                    categoryId = it.categoryId.value,
                    totalMinor = it.total.amountMinorUnits,
                    transactionCount = it.transactionCount,
                )
            },
            monthlyTotals = result.monthlyTotals.map {
                CachedMonthlyTotal(
                    yearMonth = it.yearMonth.value,
                    totalMinor = it.total.amountMinorUnits,
                )
            },
            merchantTotals = result.merchantTotals.map {
                CachedMerchantTotal(
                    merchantKey = it.merchantKey.value,
                    displayName = it.displayName,
                    totalMinor = it.total.amountMinorUnits,
                    transactionCount = it.transactionCount,
                )
            },
            subscriptionMonthlyMinor = result.subscriptionTotals.estimatedMonthly.amountMinorUnits,
            subscriptionAnnualMinor = result.subscriptionTotals.estimatedAnnual.amountMinorUnits,
            subscriptionActiveCount = result.subscriptionTotals.activeOrSuspectedCount,
            recentTransactionIds = result.recentTransactions.map { it.id.value },
            suspectedSubscriptions = result.suspectedSubscriptions.map {
                CachedSubscriptionSummary(
                    id = it.id.value,
                    merchantDisplayName = it.merchantDisplayName,
                    status = it.status.name,
                    estimatedAmountMinor = it.estimatedAmount?.amountMinorUnits,
                    evidenceCount = it.evidenceCount,
                )
            },
            lastAnalysisAt = result.lastAnalysisAt?.toEpochMillis,
        )
        val cacheKey = cacheKey(result.period)
        dashboardCacheDao.upsert(
            DashboardCacheEntity(
                cacheKey = cacheKey,
                periodStart = result.period.start.toEpochMillis,
                periodEnd = result.period.end.toEpochMillis,
                payloadJson = json.encodeToString(DashboardCachePayload.serializer(), payload),
                sourceRevision = result.transactionCount.toLong(),
                calculatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun invalidateAll() {
        dashboardCacheDao.deleteAll()
    }

    private fun cacheKey(period: AnalysisPeriod): String =
        "dashboard:${period.start.toEpochMillis}:${period.end.toEpochMillis}"
}

@Serializable
private data class DashboardCachePayload(
    val currency: String,
    val grossMinor: Long,
    val netMinor: Long,
    val creditsMinor: Long,
    val refundsMinor: Long,
    val transactionCount: Int,
    val categoryTotals: List<CachedCategoryTotal>,
    val monthlyTotals: List<CachedMonthlyTotal>,
    val merchantTotals: List<CachedMerchantTotal>,
    val subscriptionMonthlyMinor: Long,
    val subscriptionAnnualMinor: Long,
    val subscriptionActiveCount: Int,
    val recentTransactionIds: List<String>,
    val suspectedSubscriptions: List<CachedSubscriptionSummary>,
    val lastAnalysisAt: Long?,
)

@Serializable
private data class CachedCategoryTotal(
    val categoryId: String,
    val totalMinor: Long,
    val transactionCount: Int,
)

@Serializable
private data class CachedMonthlyTotal(
    val yearMonth: String,
    val totalMinor: Long,
)

@Serializable
private data class CachedMerchantTotal(
    val merchantKey: String,
    val displayName: String,
    val totalMinor: Long,
    val transactionCount: Int,
)

@Serializable
private data class CachedSubscriptionSummary(
    val id: String,
    val merchantDisplayName: String?,
    val status: String,
    val estimatedAmountMinor: Long?,
    val evidenceCount: Int,
)
