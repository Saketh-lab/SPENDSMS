package com.spendsms.app.application.dashboard

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.port.DashboardCachePort
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.application.subscriptions.SubscriptionDetectionService
import com.spendsms.app.application.subscriptions.SubscriptionIdFactory
import com.spendsms.app.domain.dashboard.DashboardCalculator
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.TransactionSemantics
import com.spendsms.app.domain.subscriptions.SubscriptionDetectionPolicy
import com.spendsms.app.domain.subscriptions.SubscriptionDetector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DashboardServiceTest {

    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(100_000L))

    @Test
    fun compute_usesSingleBulkExclusionQuery_notPerTransaction() = runBlocking {
        val transactions = (1..400).map { i -> sampleTx(i) }
        val excluded = setOf(TransactionId.of("tx-1"), TransactionId.of("tx-2"))
        val txRepo = mockk<TransactionRepository>()
        val correctionRepo = mockk<UserCorrectionRepository>()
        val cache = mockk<DashboardCachePort>(relaxed = true)
        coEvery { txRepo.findInPeriod(period) } returns transactions
        coEvery { correctionRepo.findTransactionIdsWithField(CorrectionField.NOT_A_TRANSACTION) } returns excluded
        coEvery { correctionRepo.findByTransactionId(match { true }) } returns emptyList()

        val service = DashboardService(
            transactionRepository = txRepo,
            subscriptionRepository = mockk { coEvery { listAll() } returns emptyList() },
            userCorrectionRepository = correctionRepo,
            scanStateRepository = mockk { coEvery { findLatestCompleted() } returns null },
            dashboardCachePort = cache,
            calculator = DashboardCalculator(TransactionSemantics()),
        )

        val started = System.nanoTime()
        val result = service.compute(period)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertThat(result.transactionCount).isEqualTo(400)
        assertThat(elapsedMs).isLessThan(5_000L)
        coVerify(exactly = 1) {
            correctionRepo.findTransactionIdsWithField(CorrectionField.NOT_A_TRANSACTION)
        }
        coVerify(exactly = 0) { correctionRepo.findByTransactionId(match { true }) }
    }

    @Test
    fun getDashboard_usesCacheWithoutRecompute() = runBlocking {
        val cached = DashboardCalculator(TransactionSemantics()).calculate(
            period = period,
            transactions = emptyList(),
            subscriptions = emptyList(),
        )
        val cache = mockk<DashboardCachePort>()
        val txRepo = mockk<TransactionRepository>(relaxed = true)
        coEvery { cache.get(period) } returns cached

        val service = DashboardService(
            transactionRepository = txRepo,
            subscriptionRepository = mockk(relaxed = true),
            userCorrectionRepository = mockk(relaxed = true),
            scanStateRepository = mockk(relaxed = true),
            dashboardCachePort = cache,
            calculator = DashboardCalculator(TransactionSemantics()),
        )
        assertThat(service.getDashboard(period)).isEqualTo(cached)
        coVerify(exactly = 0) { txRepo.findInPeriod(match { true }) }
    }

    @Test
    fun subscriptionDetect_usesSingleBulkExclusionQuery() = runBlocking {
        val txRepo = mockk<TransactionRepository>()
        val correctionRepo = mockk<UserCorrectionRepository>()
        coEvery { txRepo.findInPeriod(period) } returns (1..200).map { sampleTx(it) }
        coEvery { correctionRepo.findTransactionIdsWithField(CorrectionField.NOT_A_TRANSACTION) } returns emptySet()
        coEvery { correctionRepo.findByTransactionId(match { true }) } returns emptyList()

        val service = SubscriptionDetectionService(
            transactionRepository = txRepo,
            subscriptionRepository = mockk {
                coEvery { listAll() } returns emptyList()
            },
            userCorrectionRepository = correctionRepo,
            detector = SubscriptionDetector(TransactionSemantics(), SubscriptionDetectionPolicy.Phase0),
            idFactory = SubscriptionIdFactory { key ->
                com.spendsms.app.domain.model.SubscriptionId.of("sub-${key.value}")
            },
        )
        service.detectAndPersist(period = period, now = EpochMillis.of(50_000L))
        coVerify(exactly = 1) {
            correctionRepo.findTransactionIdsWithField(CorrectionField.NOT_A_TRANSACTION)
        }
        coVerify(exactly = 0) { correctionRepo.findByTransactionId(match { true }) }
    }

    private fun sampleTx(index: Int): Transaction {
        val at = EpochMillis.of(2_000L + index)
        return Transaction(
            id = TransactionId.of("tx-$index"),
            sourceMessageHash = "hash-$index",
            fingerprint = TransactionFingerprint.of("fp-$index"),
            timestamp = at,
            amount = Money.ofMinorUnits(100L + index, CurrencyCode.INR),
            merchant = Merchant(MerchantKey.of("swiggy"), "Swiggy", "SWIGGY"),
            institution = "Example Bank",
            maskedAccount = null,
            referenceHash = null,
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.UPI,
            categoryId = SystemCategories.FOOD_AND_DINING,
            confidence = Confidence.of(0.9),
            parserVersion = ParserVersion.of("v1"),
            duplicateStatus = DuplicateStatus.NONE,
            possibleDuplicateOf = null,
            transferStatus = TransferStatus.NONE,
            isUserConfirmed = false,
            createdAt = at,
            updatedAt = at,
        )
    }
}
