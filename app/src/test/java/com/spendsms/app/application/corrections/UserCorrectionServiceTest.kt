package com.spendsms.app.application.corrections

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.DashboardCachePort
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.SubscriptionRepository
import com.spendsms.app.application.port.TransactionQuery
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.application.subscriptions.SubscriptionDetectionService
import com.spendsms.app.application.subscriptions.SubscriptionIdFactory
import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.dashboard.DashboardCalculator
import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.merchant.MerchantNormalizer
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CorrectionId
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
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
import com.spendsms.app.domain.subscriptions.SubscriptionDetectionPolicy
import com.spendsms.app.domain.subscriptions.SubscriptionDetector
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UserCorrectionServiceTest {

    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(100_000L))
    private val now = EpochMillis.of(50_000L)
    private val day = SubscriptionDetectionPolicy.DAY_MILLIS

    @Test
    fun categoryCorrection_updatesTransactionAndDashboard() = runBlocking {
        val txs = InMemoryTxRepo(
            mutableListOf(
                debit("t1", "swiggy", 100_00L, 10_000L, SystemCategories.OTHER),
            ),
        )
        val corrections = InMemoryCorrectionRepo()
        val cache = RecordingCache()
        val service = service(txs, corrections, InMemorySubRepo(), cache)

        val result = service.apply(
            ApplyCorrectionRequest(
                transactionId = TransactionId.of("t1"),
                field = CorrectionField.CATEGORY,
                newValue = SystemCategories.FOOD_AND_DINING.value,
                dashboardPeriod = period,
            ),
            now = now,
        )

        assertThat(result).isInstanceOf(ApplyCorrectionResult.Applied::class.java)
        assertThat(txs.rows.single().categoryId).isEqualTo(SystemCategories.FOOD_AND_DINING)
        assertThat(txs.rows.single().isUserConfirmed).isTrue()
        assertThat(cache.invalidated).isTrue()
        assertThat(cache.lastPut?.categoryTotals?.single()?.categoryId)
            .isEqualTo(SystemCategories.FOOD_AND_DINING)
        assertThat(corrections.rows).hasSize(1)
        assertThat(corrections.rows.single().field).isEqualTo(CorrectionField.CATEGORY)
    }

    @Test
    fun directionCorrection_toTransfer_removesFromGrossSpending() = runBlocking {
        val txs = InMemoryTxRepo(
            mutableListOf(debit("t1", "friend", 500_00L, 10_000L, SystemCategories.OTHER)),
        )
        val cache = RecordingCache()
        val service = service(txs, InMemoryCorrectionRepo(), InMemorySubRepo(), cache)

        service.apply(
            ApplyCorrectionRequest(
                transactionId = TransactionId.of("t1"),
                field = CorrectionField.DIRECTION,
                newValue = TransactionDirection.TRANSFER.name,
                dashboardPeriod = period,
            ),
            now = now,
        )

        assertThat(cache.lastPut?.grossSpending?.amountMinorUnits).isEqualTo(0L)
        assertThat(txs.rows.single().direction).isEqualTo(TransactionDirection.TRANSFER)
    }

    @Test
    fun notATransaction_excludesFromDashboardWithoutDeletingRow() = runBlocking {
        val txs = InMemoryTxRepo(
            mutableListOf(debit("t1", "noise", 80_00L, 10_000L, SystemCategories.OTHER)),
        )
        val corrections = InMemoryCorrectionRepo()
        val cache = RecordingCache()
        val service = service(txs, corrections, InMemorySubRepo(), cache)

        service.apply(
            ApplyCorrectionRequest(
                transactionId = TransactionId.of("t1"),
                field = CorrectionField.NOT_A_TRANSACTION,
                newValue = "true",
                dashboardPeriod = period,
            ),
            now = now,
        )

        assertThat(txs.rows).hasSize(1)
        assertThat(corrections.rows.single().field).isEqualTo(CorrectionField.NOT_A_TRANSACTION)
        assertThat(cache.lastPut?.grossSpending?.amountMinorUnits).isEqualTo(0L)
    }

    @Test
    fun subscriptionStatusCorrection_updatesSubscriptionRow() = runBlocking {
        val txs = InMemoryTxRepo(
            mutableListOf(debit("t1", "netflix", 499_00L, 10_000L, SystemCategories.SUBSCRIPTIONS)),
        )
        val subs = InMemorySubRepo(
            mutableListOf(
                Subscription(
                    id = SubscriptionId.of("s1"),
                    merchantKey = MerchantKey.of("netflix"),
                    merchantDisplayName = "Netflix",
                    frequency = SubscriptionFrequency.MONTHLY,
                    estimatedAmount = Money.ofMinorUnits(499_00L, CurrencyCode.INR),
                    currency = CurrencyCode.INR,
                    lastPaymentDate = EpochMillis.of(10_000L),
                    estimatedNextDate = EpochMillis.of(10_000L + 30 * day),
                    confidence = Confidence.of(0.8),
                    status = SubscriptionStatus.SUSPECTED,
                    evidenceTransactionIds = listOf(TransactionId.of("t1")),
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        val service = service(txs, InMemoryCorrectionRepo(), subs, RecordingCache())

        val result = service.apply(
            ApplyCorrectionRequest(
                transactionId = TransactionId.of("t1"),
                field = CorrectionField.SUBSCRIPTION_STATUS,
                newValue = SubscriptionStatus.CONFIRMED.name,
            ),
            now = now,
        ) as ApplyCorrectionResult.Applied

        assertThat(result.subscription?.status).isEqualTo(SubscriptionStatus.CONFIRMED)
        assertThat(subs.rows.single().status).isEqualTo(SubscriptionStatus.CONFIRMED)
    }

    @Test
    fun correctionPrecedence_futureRule_isStoredForClassification() = runBlocking {
        val txs = InMemoryTxRepo(
            mutableListOf(debit("t1", "swiggy", 100_00L, 10_000L, SystemCategories.OTHER)),
        )
        val corrections = InMemoryCorrectionRepo()
        val service = service(txs, corrections, InMemorySubRepo(), RecordingCache())

        service.apply(
            ApplyCorrectionRequest(
                transactionId = TransactionId.of("t1"),
                field = CorrectionField.CATEGORY,
                newValue = SystemCategories.FOOD_AND_DINING.value,
                applyToFuture = true,
                merchantMatchKey = MerchantKey.of("swiggy"),
            ),
            now = now,
        )

        val future = corrections.findFutureRules(MerchantKey.of("swiggy"), CorrectionField.CATEGORY)
        assertThat(future).hasSize(1)
        assertThat(future.single().newValue).isEqualTo(SystemCategories.FOOD_AND_DINING.value)
    }

    @Test
    fun merchantCorrection_triggersSubscriptionRecompute() = runBlocking {
        val base = 1_700_000_000_000L
        val txs = InMemoryTxRepo(
            mutableListOf(
                debit("t1", "unknownco", 199_00L, base - 60 * day, SystemCategories.OTHER),
                debit("t2", "unknownco", 199_00L, base - 30 * day, SystemCategories.OTHER),
                debit("t3", "unknownco", 199_00L, base, SystemCategories.OTHER),
            ),
        )
        val subs = InMemorySubRepo()
        val service = service(txs, InMemoryCorrectionRepo(), subs, RecordingCache())

        service.apply(
            ApplyCorrectionRequest(
                transactionId = TransactionId.of("t1"),
                field = CorrectionField.MERCHANT,
                newValue = "UnknownCo",
                dashboardPeriod = AnalysisPeriod(
                    EpochMillis.of(base - 90 * day),
                    EpochMillis.of(base + day),
                ),
            ),
            now = EpochMillis.of(base),
        )

        assertThat(subs.rows).isNotEmpty()
        assertThat(subs.rows.single().merchantKey.value).isEqualTo("unknownco")
    }

    private fun service(
        txs: InMemoryTxRepo,
        corrections: InMemoryCorrectionRepo,
        subs: InMemorySubRepo,
        cache: RecordingCache,
    ): UserCorrectionService {
        val semantics = TransactionSemantics()
        val detector = SubscriptionDetector(semantics, SubscriptionDetectionPolicy.Phase0)
        val subscriptionDetection = SubscriptionDetectionService(
            transactionRepository = txs,
            subscriptionRepository = subs,
            userCorrectionRepository = corrections,
            detector = detector,
            idFactory = SubscriptionIdFactory { key -> SubscriptionId.of("sub-${key.value}") },
        )
        val dashboard = DashboardService(
            transactionRepository = txs,
            subscriptionRepository = subs,
            userCorrectionRepository = corrections,
            scanStateRepository = EmptyScanRepo(),
            dashboardCachePort = cache,
            calculator = DashboardCalculator(semantics),
        )
        return UserCorrectionService(
            transactionRepository = txs,
            userCorrectionRepository = corrections,
            subscriptionRepository = subs,
            merchantNormalizer = MerchantNormalizer(),
            semantics = semantics,
            dashboardService = dashboard,
            subscriptionDetectionService = subscriptionDetection,
            correctionIdFactory = CorrectionIdFactory { CorrectionId.of("c-${System.nanoTime()}") },
        )
    }

    private fun debit(
        id: String,
        merchant: String,
        amount: Long,
        at: Long,
        category: com.spendsms.app.domain.model.CategoryId,
    ): Transaction = Transaction(
        id = TransactionId.of(id),
        sourceMessageHash = "h-$id",
        fingerprint = TransactionFingerprint.of("fp-$id"),
        timestamp = EpochMillis.of(at),
        amount = Money.ofMinorUnits(amount, CurrencyCode.INR),
        merchant = Merchant(MerchantKey.of(merchant), merchant),
        institution = null,
        maskedAccount = null,
        referenceHash = null,
        direction = TransactionDirection.DEBIT,
        paymentMethod = PaymentMethod.UPI,
        categoryId = category,
        confidence = Confidence.of(0.9),
        parserVersion = ParserVersion.of("v1"),
        duplicateStatus = DuplicateStatus.NONE,
        possibleDuplicateOf = null,
        transferStatus = TransferStatus.NONE,
        isUserConfirmed = false,
        createdAt = EpochMillis.of(at),
        updatedAt = EpochMillis.of(at),
    )
}

class SubscriptionDetectionServiceTest {

    private val day = SubscriptionDetectionPolicy.DAY_MILLIS
    private val now = EpochMillis.of(1_700_000_000_000L)

    @Test
    fun dismissedWithoutNewEvidence_isNotResurrected() = runBlocking {
        val txs = InMemoryTxRepo(
            mutableListOf(
                debit("t1", "netflix", 499_00L, now.toEpochMillis - 60 * day),
                debit("t2", "netflix", 499_00L, now.toEpochMillis - 30 * day),
                debit("t3", "netflix", 499_00L, now.toEpochMillis),
            ),
        )
        val subs = InMemorySubRepo(
            mutableListOf(
                Subscription(
                    id = SubscriptionId.of("s1"),
                    merchantKey = MerchantKey.of("netflix"),
                    merchantDisplayName = "Netflix",
                    frequency = SubscriptionFrequency.MONTHLY,
                    estimatedAmount = Money.ofMinorUnits(499_00L, CurrencyCode.INR),
                    currency = CurrencyCode.INR,
                    lastPaymentDate = now,
                    estimatedNextDate = EpochMillis.of(now.toEpochMillis + 30 * day),
                    confidence = Confidence.of(0.8),
                    status = SubscriptionStatus.DISMISSED,
                    evidenceTransactionIds = listOf(
                        TransactionId.of("t1"),
                        TransactionId.of("t2"),
                        TransactionId.of("t3"),
                    ),
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        val service = SubscriptionDetectionService(
            transactionRepository = txs,
            subscriptionRepository = subs,
            userCorrectionRepository = InMemoryCorrectionRepo(),
            detector = SubscriptionDetector(TransactionSemantics(), SubscriptionDetectionPolicy.Phase0),
            idFactory = SubscriptionIdFactory { SubscriptionId.of("new") },
        )

        val persisted = service.detectAndPersist(now = now)
        assertThat(persisted.single().status).isEqualTo(SubscriptionStatus.DISMISSED)
        assertThat(subs.rows.single().id.value).isEqualTo("s1")
    }

    @Test
    fun dismissedWithNewEvidence_resurfacesAsSuspected() = runBlocking {
        val txs = InMemoryTxRepo(
            mutableListOf(
                debit("t1", "netflix", 499_00L, now.toEpochMillis - 90 * day),
                debit("t2", "netflix", 499_00L, now.toEpochMillis - 60 * day),
                debit("t3", "netflix", 499_00L, now.toEpochMillis - 30 * day),
                debit("t4", "netflix", 499_00L, now.toEpochMillis),
            ),
        )
        val subs = InMemorySubRepo(
            mutableListOf(
                Subscription(
                    id = SubscriptionId.of("s1"),
                    merchantKey = MerchantKey.of("netflix"),
                    merchantDisplayName = "Netflix",
                    frequency = SubscriptionFrequency.MONTHLY,
                    estimatedAmount = Money.ofMinorUnits(499_00L, CurrencyCode.INR),
                    currency = CurrencyCode.INR,
                    lastPaymentDate = EpochMillis.of(now.toEpochMillis - 30 * day),
                    estimatedNextDate = now,
                    confidence = Confidence.of(0.8),
                    status = SubscriptionStatus.DISMISSED,
                    evidenceTransactionIds = listOf(
                        TransactionId.of("t1"),
                        TransactionId.of("t2"),
                        TransactionId.of("t3"),
                    ),
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        val service = SubscriptionDetectionService(
            transactionRepository = txs,
            subscriptionRepository = subs,
            userCorrectionRepository = InMemoryCorrectionRepo(),
            detector = SubscriptionDetector(TransactionSemantics(), SubscriptionDetectionPolicy.Phase0),
            idFactory = SubscriptionIdFactory { SubscriptionId.of("new") },
        )

        val persisted = service.detectAndPersist(now = now)
        assertThat(persisted.single().status).isEqualTo(SubscriptionStatus.SUSPECTED)
        assertThat(persisted.single().evidenceTransactionIds).hasSize(4)
        assertThat(persisted.single().id.value).isEqualTo("s1")
    }

    @Test
    fun confirmedStatus_isPreservedOnRedetect() = runBlocking {
        val txs = InMemoryTxRepo(
            mutableListOf(
                debit("t1", "spotify", 119_00L, now.toEpochMillis - 60 * day),
                debit("t2", "spotify", 119_00L, now.toEpochMillis - 30 * day),
                debit("t3", "spotify", 119_00L, now.toEpochMillis),
            ),
        )
        val subs = InMemorySubRepo(
            mutableListOf(
                Subscription(
                    id = SubscriptionId.of("s1"),
                    merchantKey = MerchantKey.of("spotify"),
                    merchantDisplayName = "Spotify",
                    frequency = SubscriptionFrequency.MONTHLY,
                    estimatedAmount = Money.ofMinorUnits(119_00L, CurrencyCode.INR),
                    currency = CurrencyCode.INR,
                    lastPaymentDate = now,
                    estimatedNextDate = EpochMillis.of(now.toEpochMillis + 30 * day),
                    confidence = Confidence.of(0.9),
                    status = SubscriptionStatus.CONFIRMED,
                    evidenceTransactionIds = listOf(TransactionId.of("t1"), TransactionId.of("t2")),
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        val service = SubscriptionDetectionService(
            transactionRepository = txs,
            subscriptionRepository = subs,
            userCorrectionRepository = InMemoryCorrectionRepo(),
            detector = SubscriptionDetector(TransactionSemantics(), SubscriptionDetectionPolicy.Phase0),
            idFactory = SubscriptionIdFactory { SubscriptionId.of("new") },
        )

        assertThat(service.detectAndPersist(now = now).single().status)
            .isEqualTo(SubscriptionStatus.CONFIRMED)
    }

    private fun debit(
        id: String,
        merchant: String,
        amount: Long,
        at: Long,
    ): Transaction = Transaction(
        id = TransactionId.of(id),
        sourceMessageHash = "h-$id",
        fingerprint = TransactionFingerprint.of("fp-$id"),
        timestamp = EpochMillis.of(at),
        amount = Money.ofMinorUnits(amount, CurrencyCode.INR),
        merchant = Merchant(MerchantKey.of(merchant), merchant),
        institution = null,
        maskedAccount = null,
        referenceHash = null,
        direction = TransactionDirection.DEBIT,
        paymentMethod = PaymentMethod.UPI,
        categoryId = SystemCategories.SUBSCRIPTIONS,
        confidence = Confidence.of(0.9),
        parserVersion = ParserVersion.of("v1"),
        duplicateStatus = DuplicateStatus.NONE,
        possibleDuplicateOf = null,
        transferStatus = TransferStatus.NONE,
        isUserConfirmed = false,
        createdAt = EpochMillis.of(at),
        updatedAt = EpochMillis.of(at),
    )
}

private class InMemoryTxRepo(
    val rows: MutableList<Transaction> = mutableListOf(),
) : TransactionRepository {
    override suspend fun findById(id: TransactionId): Transaction? = rows.find { it.id == id }
    override suspend fun findByFingerprint(fingerprint: TransactionFingerprint): Transaction? =
        rows.find { it.fingerprint == fingerprint }
    override suspend fun findBySourceMessageHash(sourceMessageHash: String): Transaction? =
        rows.find { it.sourceMessageHash == sourceMessageHash }
    override suspend fun upsert(transaction: Transaction): Transaction {
        val index = rows.indexOfFirst { it.id == transaction.id }
        if (index >= 0) rows[index] = transaction else rows += transaction
        return transaction
    }
    override suspend fun upsertAll(transactions: List<Transaction>) {
        transactions.forEach { upsert(it) }
    }
    override suspend fun update(transaction: Transaction): Transaction = upsert(transaction)
    override suspend fun findInPeriod(period: AnalysisPeriod): List<Transaction> =
        rows.filter {
            it.timestamp.toEpochMillis in period.start.toEpochMillis..period.end.toEpochMillis
        }
    override suspend fun findNear(
        amount: Money,
        around: EpochMillis,
        windowMillis: Long,
    ): List<Transaction> = rows.filter {
        it.amount == amount && abs(it.timestamp.toEpochMillis - around.toEpochMillis) <= windowMillis
    }
    override suspend fun findByMerchantKey(
        merchantKey: MerchantKey,
        period: AnalysisPeriod?,
    ): List<Transaction> = rows.filter { it.merchant?.key == merchantKey }
    override suspend fun query(query: TransactionQuery): List<Transaction> = rows.toList()
    override suspend fun deleteAllAnalysed() {
        rows.clear()
    }
}

private class InMemoryCorrectionRepo(
    val rows: MutableList<UserCorrection> = mutableListOf(),
) : UserCorrectionRepository {
    override suspend fun findById(id: CorrectionId): UserCorrection? = rows.find { it.id == id }
    override suspend fun findByTransactionId(transactionId: TransactionId): List<UserCorrection> =
        rows.filter { it.transactionId == transactionId }
    override suspend fun findFutureRules(
        merchantMatchKey: MerchantKey,
        field: CorrectionField,
    ): List<UserCorrection> = rows.filter {
        it.applyToFuture && it.merchantMatchKey == merchantMatchKey && it.field == field
    }
    override suspend fun save(correction: UserCorrection): UserCorrection {
        rows += correction
        return correction
    }
    override suspend fun deleteByTransactionId(transactionId: TransactionId) {
        rows.removeAll { it.transactionId == transactionId }
    }
    override suspend fun deleteAll() {
        rows.clear()
    }
}

private class InMemorySubRepo(
    val rows: MutableList<Subscription> = mutableListOf(),
) : SubscriptionRepository {
    override suspend fun findById(id: SubscriptionId): Subscription? = rows.find { it.id == id }
    override suspend fun findByMerchantKey(merchantKey: MerchantKey): Subscription? =
        rows.find { it.merchantKey == merchantKey }
    override suspend fun findByStatus(status: SubscriptionStatus): List<Subscription> =
        rows.filter { it.status == status }
    override suspend fun listAll(): List<Subscription> = rows.toList()
    override suspend fun upsert(subscription: Subscription): Subscription {
        val index = rows.indexOfFirst { it.id == subscription.id }
        if (index >= 0) rows[index] = subscription else rows += subscription
        return subscription
    }
    override suspend fun updateStatus(id: SubscriptionId, status: SubscriptionStatus): Subscription? {
        val existing = findById(id) ?: return null
        return upsert(existing.copy(status = status))
    }
    override suspend fun deleteAll() {
        rows.clear()
    }
}

private class RecordingCache : DashboardCachePort {
    var invalidated: Boolean = false
    var lastPut: DashboardResult? = null
    override suspend fun get(period: AnalysisPeriod): DashboardResult? = null
    override suspend fun put(result: DashboardResult) {
        lastPut = result
    }
    override suspend fun invalidateAll() {
        invalidated = true
        lastPut = null
    }
}

private class EmptyScanRepo : ScanStateRepository {
    override suspend fun findById(id: ScanId): ScanState? = null
    override suspend fun findActive(): ScanState? = null
    override suspend fun findResumable(): ScanState? = null
    override suspend fun findLatestCompleted(): ScanState? = null
    override suspend fun save(state: ScanState): ScanState = state
    override suspend fun deleteAll() = Unit
}
