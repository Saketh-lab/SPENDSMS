package com.spendsms.app.domain

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.dashboard.CategoryTotal
import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.domain.dashboard.MerchantTotal
import com.spendsms.app.domain.dashboard.SubscriptionSummary
import com.spendsms.app.domain.dashboard.SubscriptionTotals
import com.spendsms.app.domain.dashboard.YearMonthKey
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CorrectionId
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.subscriptions.Subscription
import com.spendsms.app.domain.model.SubscriptionFrequency
import org.junit.Assert.assertThrows
import org.junit.Test

class CorrectionAndSubscriptionTest {

    private val now = EpochMillis.of(1_725_000_000_000L)

    @Test
    fun applyToFuture_requiresMerchantMatchKey() {
        assertThrows(IllegalArgumentException::class.java) {
            UserCorrection(
                id = CorrectionId.of("c1"),
                transactionId = TransactionId.of("t1"),
                field = CorrectionField.CATEGORY,
                oldValue = SystemCategories.OTHER.value,
                newValue = SystemCategories.GROCERIES.value,
                applyToFuture = true,
                merchantMatchKey = null,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    @Test
    fun correction_persistsFieldAndPrecedenceInputs() {
        val correction = UserCorrection(
            id = CorrectionId.of("c1"),
            transactionId = TransactionId.of("t1"),
            field = CorrectionField.MERCHANT,
            oldValue = "OLD",
            newValue = "NEW",
            applyToFuture = true,
            merchantMatchKey = MerchantKey.of("merchant"),
            createdAt = now,
            updatedAt = now,
        )
        assertThat(correction.field).isEqualTo(CorrectionField.MERCHANT)
        assertThat(correction.applyToFuture).isTrue()
    }

    @Test
    fun subscription_rejectsCurrencyMismatchOnEstimatedAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            Subscription(
                id = SubscriptionId.of("s1"),
                merchantKey = MerchantKey.of("netflix"),
                merchantDisplayName = "Netflix",
                frequency = SubscriptionFrequency.MONTHLY,
                estimatedAmount = Money.ofMinorUnits(499_00L, CurrencyCode.of("USD")),
                currency = CurrencyCode.INR,
                lastPaymentDate = now,
                estimatedNextDate = EpochMillis.of(now.toEpochMillis + 1),
                confidence = Confidence.of(0.8),
                status = SubscriptionStatus.SUSPECTED,
                evidenceTransactionIds = listOf(TransactionId.of("t1"), TransactionId.of("t2")),
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    @Test
    fun subscriptionStatus_matchesStep3Vocabulary() {
        assertThat(SubscriptionStatus.entries.map { it.name }).containsExactly(
            "SUSPECTED",
            "CONFIRMED",
            "DISMISSED",
            "POSSIBLY_INACTIVE",
        ).inOrder()
    }
}

class DashboardResultTest {

    @Test
    fun yearMonthKey_validatesFormat() {
        assertThat(YearMonthKey.of("2026-08").value).isEqualTo("2026-08")
        assertThrows(IllegalArgumentException::class.java) {
            YearMonthKey.of("2026-13")
        }
        assertThrows(IllegalArgumentException::class.java) {
            YearMonthKey.of("26-08")
        }
    }

    @Test
    fun dashboardResult_rejectsCurrencyMismatch() {
        val period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L))
        assertThrows(IllegalArgumentException::class.java) {
            DashboardResult(
                period = period,
                grossSpending = Money.ofMinorUnits(100L, CurrencyCode.INR),
                netSpending = Money.ofMinorUnits(80L, CurrencyCode.of("USD")),
                credits = Money.zero(),
                refunds = Money.zero(),
                transactionCount = 1,
                categoryTotals = listOf(
                    CategoryTotal(SystemCategories.OTHER, Money.ofMinorUnits(100L, CurrencyCode.INR), 1),
                ),
                monthlyTotals = emptyList(),
                merchantTotals = listOf(
                    MerchantTotal(
                        merchantKey = MerchantKey.of("m1"),
                        displayName = "M1",
                        total = Money.ofMinorUnits(100L, CurrencyCode.INR),
                        transactionCount = 1,
                    ),
                ),
                subscriptionTotals = SubscriptionTotals(
                    estimatedMonthly = Money.zero(),
                    estimatedAnnual = Money.zero(),
                    activeOrSuspectedCount = 0,
                ),
                recentTransactions = emptyList(),
                suspectedSubscriptions = emptyList(),
                lastAnalysisAt = null,
            )
        }
    }

    @Test
    fun dashboardResult_acceptsConsistentAggregates() {
        val inr = CurrencyCode.INR
        val period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L))
        val result = DashboardResult(
            period = period,
            grossSpending = Money.ofMinorUnits(100L, inr),
            netSpending = Money.ofMinorUnits(80L, inr),
            credits = Money.ofMinorUnits(10L, inr),
            refunds = Money.ofMinorUnits(10L, inr),
            transactionCount = 2,
            categoryTotals = emptyList(),
            monthlyTotals = emptyList(),
            merchantTotals = emptyList(),
            subscriptionTotals = SubscriptionTotals(
                estimatedMonthly = Money.ofMinorUnits(499_00L, inr),
                estimatedAnnual = Money.ofMinorUnits(5_988_00L, inr),
                activeOrSuspectedCount = 1,
            ),
            recentTransactions = emptyList(),
            suspectedSubscriptions = listOf(
                SubscriptionSummary(
                    id = SubscriptionId.of("s1"),
                    merchantDisplayName = "Music",
                    status = SubscriptionStatus.SUSPECTED,
                    estimatedAmount = Money.ofMinorUnits(499_00L, inr),
                    evidenceCount = 3,
                ),
            ),
            lastAnalysisAt = EpochMillis.of(3L),
        )
        assertThat(result.transactionCount).isEqualTo(2)
        assertThat(result.suspectedSubscriptions).hasSize(1)
    }
}
