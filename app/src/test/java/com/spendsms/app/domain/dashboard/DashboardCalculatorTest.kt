package com.spendsms.app.domain.dashboard

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.SubscriptionFrequency
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.TransactionSemantics
import com.spendsms.app.domain.subscriptions.Subscription
import org.junit.Test

class DashboardCalculatorTest {

    private val calculator = DashboardCalculator(TransactionSemantics())
    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(10_000L))

    @Test
    fun totals_grossNetCreditsRefunds_useSemantics() {
        val txs = listOf(
            tx("d1", TransactionDirection.DEBIT, 100_00L, 2_000L, SystemCategories.FOOD_AND_DINING, "swiggy"),
            tx("d2", TransactionDirection.DEBIT, 50_00L, 3_000L, SystemCategories.GROCERIES, "blinkit"),
            tx("c1", TransactionDirection.CREDIT, 20_00L, 4_000L, SystemCategories.INCOME_AND_REFUNDS, null),
            tx("r1", TransactionDirection.REFUND, 30_00L, 5_000L, SystemCategories.INCOME_AND_REFUNDS, "swiggy"),
            tx(
                "t1",
                TransactionDirection.DEBIT,
                200_00L,
                6_000L,
                SystemCategories.TRANSFERS,
                "own account",
                transferStatus = TransferStatus.CONFIRMED,
            ),
        )
        val result = calculator.calculate(period, txs, subscriptions = emptyList())
        assertThat(result.grossSpending.amountMinorUnits).isEqualTo(150_00L)
        assertThat(result.refunds.amountMinorUnits).isEqualTo(30_00L)
        assertThat(result.netSpending.amountMinorUnits).isEqualTo(120_00L)
        assertThat(result.credits.amountMinorUnits).isEqualTo(20_00L)
        assertThat(result.transactionCount).isEqualTo(5)
    }

    @Test
    fun periodBoundaries_areInclusive() {
        val txs = listOf(
            tx("a", TransactionDirection.DEBIT, 10_00L, 1_000L, SystemCategories.OTHER, "a"),
            tx("b", TransactionDirection.DEBIT, 20_00L, 10_000L, SystemCategories.OTHER, "b"),
            tx("c", TransactionDirection.DEBIT, 99_00L, 10_001L, SystemCategories.OTHER, "c"),
            tx("d", TransactionDirection.DEBIT, 99_00L, 999L, SystemCategories.OTHER, "d"),
        )
        val result = calculator.calculate(period, txs, emptyList())
        assertThat(result.grossSpending.amountMinorUnits).isEqualTo(30_00L)
        assertThat(result.transactionCount).isEqualTo(2)
    }

    @Test
    fun categoryAndMerchantSummaries_onlyCountGrossExpenses() {
        val txs = listOf(
            tx("d1", TransactionDirection.DEBIT, 100_00L, 2_000L, SystemCategories.FOOD_AND_DINING, "swiggy"),
            tx("d2", TransactionDirection.DEBIT, 40_00L, 3_000L, SystemCategories.FOOD_AND_DINING, "swiggy"),
            tx("r1", TransactionDirection.REFUND, 10_00L, 4_000L, SystemCategories.FOOD_AND_DINING, "swiggy"),
        )
        val result = calculator.calculate(period, txs, emptyList())
        assertThat(result.categoryTotals).hasSize(1)
        assertThat(result.categoryTotals.single().total.amountMinorUnits).isEqualTo(140_00L)
        assertThat(result.categoryTotals.single().transactionCount).isEqualTo(2)
        assertThat(result.merchantTotals.single().total.amountMinorUnits).isEqualTo(140_00L)
    }

    @Test
    fun confirmedDuplicatesAndExcludedIds_omittedFromMoneyTotals() {
        val txs = listOf(
            tx("d1", TransactionDirection.DEBIT, 100_00L, 2_000L, SystemCategories.OTHER, "m"),
            tx(
                "dup",
                TransactionDirection.DEBIT,
                100_00L,
                2_100L,
                SystemCategories.OTHER,
                "m",
                duplicateStatus = DuplicateStatus.CONFIRMED,
            ),
            tx("ex", TransactionDirection.DEBIT, 50_00L, 3_000L, SystemCategories.OTHER, "m"),
        )
        val result = calculator.calculate(
            period = period,
            transactions = txs,
            subscriptions = emptyList(),
            excludedTransactionIds = setOf(TransactionId.of("ex")),
        )
        assertThat(result.grossSpending.amountMinorUnits).isEqualTo(100_00L)
    }

    @Test
    fun emptyDataset_returnsZerosWithoutFabricatingInsights() {
        val result = calculator.calculate(period, emptyList(), emptyList())
        assertThat(result.grossSpending.amountMinorUnits).isEqualTo(0L)
        assertThat(result.categoryTotals).isEmpty()
        assertThat(result.merchantTotals).isEmpty()
        assertThat(result.suspectedSubscriptions).isEmpty()
    }

    @Test
    fun subscriptionTotals_convertFrequenciesToMonthly() {
        val subs = listOf(
            sub("s1", SubscriptionFrequency.MONTHLY, 100_00L, SubscriptionStatus.SUSPECTED),
            sub("s2", SubscriptionFrequency.YEARLY, 1_200_00L, SubscriptionStatus.CONFIRMED),
            sub("s3", SubscriptionFrequency.MONTHLY, 50_00L, SubscriptionStatus.DISMISSED),
        )
        val result = calculator.calculate(period, emptyList(), subs)
        assertThat(result.subscriptionTotals.estimatedMonthly.amountMinorUnits).isEqualTo(200_00L)
        assertThat(result.subscriptionTotals.estimatedAnnual.amountMinorUnits).isEqualTo(2_400_00L)
        assertThat(result.subscriptionTotals.activeOrSuspectedCount).isEqualTo(2)
        assertThat(result.suspectedSubscriptions.map { it.id.value }).containsExactly("s1", "s2")
    }

    @Test
    fun monthlyTotals_bucketByUtcYearMonth() {
        // 2024-01-15 and 2024-02-15 UTC
        val jan = 1_705_276_800_000L
        val feb = 1_707_955_200_000L
        val wide = AnalysisPeriod(EpochMillis.of(jan), EpochMillis.of(feb + 1))
        val txs = listOf(
            tx("j", TransactionDirection.DEBIT, 10_00L, jan, SystemCategories.OTHER, "a"),
            tx("f", TransactionDirection.DEBIT, 20_00L, feb, SystemCategories.OTHER, "b"),
        )
        val result = calculator.calculate(wide, txs, emptyList())
        assertThat(result.monthlyTotals.map { it.yearMonth.value }).containsExactly("2024-01", "2024-02").inOrder()
        assertThat(result.monthlyTotals[0].total.amountMinorUnits).isEqualTo(10_00L)
        assertThat(result.monthlyTotals[1].total.amountMinorUnits).isEqualTo(20_00L)
    }

    @Test
    fun calculate_largeTransactionVolumeStaysWithinBudget() {
        val txs = (1..2_000).map { i ->
            tx(
                id = "v$i",
                direction = TransactionDirection.DEBIT,
                amount = 100L + i,
                at = 2_000L + i,
                category = SystemCategories.FOOD_AND_DINING,
                merchant = "swiggy",
            )
        }
        val started = System.nanoTime()
        val result = calculator.calculate(period, txs, emptyList())
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertThat(result.transactionCount).isEqualTo(2_000)
        assertThat(result.grossSpending.amountMinorUnits).isGreaterThan(0L)
        assertThat(elapsedMs).isLessThan(3_000L)
    }

    private fun tx(
        id: String,
        direction: TransactionDirection,
        amount: Long,
        at: Long,
        category: com.spendsms.app.domain.model.CategoryId,
        merchant: String?,
        transferStatus: TransferStatus = TransferStatus.NONE,
        duplicateStatus: DuplicateStatus = DuplicateStatus.NONE,
    ): Transaction = Transaction(
        id = TransactionId.of(id),
        sourceMessageHash = "h-$id",
        fingerprint = TransactionFingerprint.of("fp-$id"),
        timestamp = EpochMillis.of(at),
        amount = Money.ofMinorUnits(amount, CurrencyCode.INR),
        merchant = merchant?.let {
            Merchant(key = MerchantKey.of(it), displayName = it)
        },
        institution = null,
        maskedAccount = null,
        referenceHash = null,
        direction = direction,
        paymentMethod = PaymentMethod.UPI,
        categoryId = category,
        confidence = Confidence.of(0.8),
        parserVersion = ParserVersion.of("v1"),
        duplicateStatus = duplicateStatus,
        possibleDuplicateOf = null,
        transferStatus = transferStatus,
        isUserConfirmed = false,
        createdAt = EpochMillis.of(at),
        updatedAt = EpochMillis.of(at),
    )

    private fun sub(
        id: String,
        frequency: SubscriptionFrequency,
        amount: Long,
        status: SubscriptionStatus,
    ): Subscription = Subscription(
        id = SubscriptionId.of(id),
        merchantKey = MerchantKey.of(id),
        merchantDisplayName = id,
        frequency = frequency,
        estimatedAmount = Money.ofMinorUnits(amount, CurrencyCode.INR),
        currency = CurrencyCode.INR,
        lastPaymentDate = EpochMillis.of(1L),
        estimatedNextDate = EpochMillis.of(2L),
        confidence = Confidence.of(0.7),
        status = status,
        evidenceTransactionIds = listOf(TransactionId.of("e-$id")),
        createdAt = EpochMillis.of(1L),
        updatedAt = EpochMillis.of(1L),
    )
}
