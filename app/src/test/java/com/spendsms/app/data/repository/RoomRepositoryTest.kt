package com.spendsms.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.port.ParserRulesDocument
import com.spendsms.app.application.port.TransactionQuery
import com.spendsms.app.data.room.SpendSmsDatabase
import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.domain.dashboard.SubscriptionTotals
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
import com.spendsms.app.domain.model.ParserBundleStatus
import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.domain.model.ScanStatus
import com.spendsms.app.domain.model.SubscriptionFrequency
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.subscriptions.Subscription
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomRepositoryTest {

    private lateinit var db: SpendSmsDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var merchants: RoomMerchantRepository
    private lateinit var corrections: RoomUserCorrectionRepository
    private lateinit var subscriptions: RoomSubscriptionRepository
    private lateinit var scans: RoomScanStateRepository
    private lateinit var parsers: RoomParserBundleRepository
    private lateinit var deletion: RoomFinancialDataDeletionPort
    private lateinit var dashboardCache: RoomDashboardCachePort

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = SpendSmsDatabase.buildInMemory(context)
        transactions = RoomTransactionRepository(db, db.transactionDao())
        categories = RoomCategoryRepository(db.categoryDao())
        merchants = RoomMerchantRepository(db.transactionDao(), MerchantNormalizer())
        corrections = RoomUserCorrectionRepository(db.userCorrectionDao())
        subscriptions = RoomSubscriptionRepository(db.subscriptionDao())
        scans = RoomScanStateRepository(db.scanStateDao())
        parsers = RoomParserBundleRepository(context, db.parserMetadataDao())
        deletion = RoomFinancialDataDeletionPort(
            db = db,
            subscriptionDao = db.subscriptionDao(),
            userCorrectionDao = db.userCorrectionDao(),
            transactionDao = db.transactionDao(),
            scanStateDao = db.scanStateDao(),
            dashboardCacheDao = db.dashboardCacheDao(),
        )
        dashboardCache = RoomDashboardCachePort(
            dashboardCacheDao = db.dashboardCacheDao(),
            transactionDao = db.transactionDao(),
            json = Json { ignoreUnknownKeys = false },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun transactionUpsert_isIdempotentByFingerprint() {
        runBlocking {
            val first = sampleTx(id = "tx-1", fingerprint = "fp-1", amount = 100L)
            val second = sampleTx(id = "tx-NEW", fingerprint = "fp-1", amount = 200L)
            transactions.upsert(first)
            val merged = transactions.upsert(second)
            assertThat(merged.id.value).isEqualTo("tx-1")
            assertThat(merged.amount.amountMinorUnits).isEqualTo(200L)
            assertThat(transactions.findByFingerprint(TransactionFingerprint.of("fp-1"))).isNotNull()
            assertThat(db.transactionDao().findById("tx-NEW")).isNull()
        }
    }

    @Test
    fun transactionQuery_andNear_workThroughRepository() {
        runBlocking {
            transactions.upsert(sampleTx("a", "fp-a", amount = 50L, timestamp = 1_000L))
            transactions.upsert(sampleTx("b", "fp-b", amount = 50L, timestamp = 2_000L))
            transactions.upsert(
                sampleTx("c", "fp-c", amount = 50L, timestamp = 2_000L).copy(
                    merchant = Merchant(MerchantKey.of("cafe"), "Cafe", "CAFE"),
                ),
            )

            val near = transactions.findNear(
                amount = Money.ofMinorUnits(50L, CurrencyCode.INR),
                around = EpochMillis.of(1_500L),
                windowMillis = 1_000L,
            )
            assertThat(near.map { it.id.value }).containsAtLeast("a", "b")

            val queried = transactions.query(
                TransactionQuery(merchantSearch = "Cafe", limit = 10),
            )
            assertThat(queried).hasSize(1)
            assertThat(queried.first().merchant?.displayName).isEqualTo("Cafe")
        }
    }

    @Test
    fun merchantResolve_fallsBackAndFindsPrior() {
        runBlocking {
            transactions.upsert(
                sampleTx("m1", "fp-m1").copy(
                    merchant = Merchant(MerchantKey.of("shop"), "Shop", "SHOP"),
                ),
            )
            val found = merchants.findByKey(MerchantKey.of("shop"))
            assertThat(found?.displayName).isEqualTo("Shop")

            val resolved = merchants.resolve("New Place", institution = "Bank")
            assertThat(resolved.key.value).isEqualTo("new place")
            assertThat(resolved.displayName).isEqualTo("New Place")
        }
    }

    @Test
    fun correctionAndSubscription_persistWithEvidence() {
        runBlocking {
            transactions.upsert(sampleTx("t1", "fp-t1"))
            transactions.upsert(sampleTx("t2", "fp-t2"))

            corrections.save(
                UserCorrection(
                    id = CorrectionId.of("c1"),
                    transactionId = TransactionId.of("t1"),
                    field = CorrectionField.CATEGORY,
                    oldValue = SystemCategories.OTHER.value,
                    newValue = SystemCategories.GROCERIES.value,
                    applyToFuture = true,
                    merchantMatchKey = MerchantKey.of("shop"),
                    createdAt = EpochMillis.of(1L),
                    updatedAt = EpochMillis.of(1L),
                ),
            )
            assertThat(corrections.findFutureRules(MerchantKey.of("shop"), CorrectionField.CATEGORY))
                .hasSize(1)

            subscriptions.upsert(
                Subscription(
                    id = SubscriptionId.of("s1"),
                    merchantKey = MerchantKey.of("music"),
                    merchantDisplayName = "Music",
                    frequency = SubscriptionFrequency.MONTHLY,
                    estimatedAmount = Money.ofMinorUnits(499_00L, CurrencyCode.INR),
                    currency = CurrencyCode.INR,
                    lastPaymentDate = EpochMillis.of(1L),
                    estimatedNextDate = EpochMillis.of(2L),
                    confidence = Confidence.of(0.8),
                    status = SubscriptionStatus.SUSPECTED,
                    evidenceTransactionIds = listOf(TransactionId.of("t1"), TransactionId.of("t2")),
                    createdAt = EpochMillis.of(1L),
                    updatedAt = EpochMillis.of(1L),
                ),
            )
            assertThat(subscriptions.findById(SubscriptionId.of("s1"))?.evidenceTransactionIds)
                .hasSize(2)
        }
    }

    @Test
    fun parserBundle_installActivateAndLoadRules_fromNoBackupFiles() {
        runBlocking {
            val version = ParserVersion.of("2026.08.12.1")
            parsers.install(
                metadata = ParserMetadata(
                    parserVersion = version,
                    rulesVersion = RulesVersion.of("r1"),
                    schemaVersion = 1,
                    checksum = "abc",
                    installedAt = EpochMillis.of(10L),
                    activatedAt = null,
                    status = ParserBundleStatus.INSTALLED,
                ),
                rulesDocument = ParserRulesDocument.of("""{"rules":[]}"""),
            )
            parsers.activate(version)
            assertThat(parsers.findActiveMetadata()?.parserVersion).isEqualTo(version)
            assertThat(parsers.loadActiveRulesDocument()?.utf8Json).contains("rules")
        }
    }

    @Test
    fun financialDeletion_clearsAnalysedData_keepsParserMetadata() {
        runBlocking {
            transactions.upsert(sampleTx("del-1", "fp-del"))
            corrections.save(
                UserCorrection(
                    id = CorrectionId.of("c-del"),
                    transactionId = TransactionId.of("del-1"),
                    field = CorrectionField.CATEGORY,
                    oldValue = SystemCategories.OTHER.value,
                    newValue = SystemCategories.GROCERIES.value,
                    applyToFuture = false,
                    merchantMatchKey = null,
                    createdAt = EpochMillis.of(1L),
                    updatedAt = EpochMillis.of(1L),
                ),
            )
            subscriptions.upsert(
                Subscription(
                    id = SubscriptionId.of("s-del"),
                    merchantKey = MerchantKey.of("music"),
                    merchantDisplayName = "Music",
                    frequency = SubscriptionFrequency.MONTHLY,
                    estimatedAmount = Money.ofMinorUnits(499_00L, CurrencyCode.INR),
                    currency = CurrencyCode.INR,
                    lastPaymentDate = EpochMillis.of(1L),
                    estimatedNextDate = EpochMillis.of(2L),
                    confidence = Confidence.of(0.8),
                    status = SubscriptionStatus.SUSPECTED,
                    evidenceTransactionIds = listOf(TransactionId.of("del-1")),
                    createdAt = EpochMillis.of(1L),
                    updatedAt = EpochMillis.of(1L),
                ),
            )
            scans.save(
                ScanState(
                    id = ScanId.of("scan-1"),
                    period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L)),
                    lastProcessedMessageId = "m1",
                    parserVersion = ParserVersion.of("v1"),
                    status = ScanStatus.COMPLETED,
                    processedCount = 1,
                    acceptedCount = 1,
                    startedAt = EpochMillis.of(1L),
                    completedAt = EpochMillis.of(2L),
                    updatedAt = EpochMillis.of(2L),
                ),
            )
            val version = ParserVersion.of("keep-me")
            parsers.install(
                ParserMetadata(
                    parserVersion = version,
                    rulesVersion = RulesVersion.of("r1"),
                    schemaVersion = 1,
                    checksum = "x",
                    installedAt = EpochMillis.of(1L),
                    activatedAt = null,
                    status = ParserBundleStatus.INSTALLED,
                ),
                ParserRulesDocument.of("{}"),
            )
            val cachePeriod = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(9_999L))
            val zero = Money.zero()
            dashboardCache.put(
                DashboardResult(
                    period = cachePeriod,
                    grossSpending = Money.ofMinorUnits(100L, CurrencyCode.INR),
                    netSpending = Money.ofMinorUnits(100L, CurrencyCode.INR),
                    credits = zero,
                    refunds = zero,
                    transactionCount = 1,
                    categoryTotals = emptyList(),
                    monthlyTotals = emptyList(),
                    merchantTotals = emptyList(),
                    subscriptionTotals = SubscriptionTotals(zero, zero, 0),
                    recentTransactions = emptyList(),
                    suspectedSubscriptions = emptyList(),
                    lastAnalysisAt = EpochMillis.of(5L),
                ),
            )

            deletion.deleteAllAnalysedData()

            assertThat(transactions.findById(TransactionId.of("del-1"))).isNull()
            assertThat(scans.findById(ScanId.of("scan-1"))).isNull()
            assertThat(corrections.findByTransactionId(TransactionId.of("del-1"))).isEmpty()
            assertThat(subscriptions.findById(SubscriptionId.of("s-del"))).isNull()
            assertThat(dashboardCache.get(cachePeriod)).isNull()
            assertThat(parsers.findMetadata(version)).isNotNull()
            assertThat(categories.getAll()).isNotEmpty()
        }
    }

    @Test
    fun scanState_findResumable_includesInterrupted_notCompleted() {
        runBlocking {
            val period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L))
            scans.save(
                ScanState(
                    id = ScanId.of("done"),
                    period = period,
                    lastProcessedMessageId = "m9",
                    parserVersion = ParserVersion.of("v1"),
                    status = ScanStatus.COMPLETED,
                    processedCount = 9,
                    acceptedCount = 4,
                    startedAt = EpochMillis.of(10L),
                    completedAt = EpochMillis.of(20L),
                    updatedAt = EpochMillis.of(20L),
                ),
            )
            scans.save(
                ScanState(
                    id = ScanId.of("paused"),
                    period = period,
                    lastProcessedMessageId = "m4",
                    parserVersion = ParserVersion.of("v1"),
                    status = ScanStatus.INTERRUPTED,
                    processedCount = 4,
                    acceptedCount = 2,
                    startedAt = EpochMillis.of(10L),
                    completedAt = null,
                    updatedAt = EpochMillis.of(30L),
                ),
            )
            assertThat(scans.findActive()).isNull()
            assertThat(scans.findResumable()?.id?.value).isEqualTo("paused")
            assertThat(scans.findResumable()?.lastProcessedMessageId).isEqualTo("m4")
        }
    }

    @Test
    fun dashboardCache_roundTripsAggregatesWithoutSms() {
        runBlocking {
            val tx = sampleTx("dash-1", "fp-dash")
            transactions.upsert(tx)
            val period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(9_999L))
            val zero = Money.zero()
            dashboardCache.put(
                DashboardResult(
                    period = period,
                    grossSpending = Money.ofMinorUnits(100L, CurrencyCode.INR),
                    netSpending = Money.ofMinorUnits(100L, CurrencyCode.INR),
                    credits = zero,
                    refunds = zero,
                    transactionCount = 1,
                    categoryTotals = emptyList(),
                    monthlyTotals = emptyList(),
                    merchantTotals = emptyList(),
                    subscriptionTotals = SubscriptionTotals(zero, zero, 0),
                    recentTransactions = listOf(tx),
                    suspectedSubscriptions = emptyList(),
                    lastAnalysisAt = EpochMillis.of(5L),
                ),
            )
            val restored = dashboardCache.get(period)
            assertThat(restored?.grossSpending?.amountMinorUnits).isEqualTo(100L)
            assertThat(restored?.recentTransactions?.map { it.id.value }).containsExactly("dash-1")
            assertThat(restored?.recentTransactions?.first()?.javaClass?.declaredFields?.map { it.name })
                .doesNotContain("body")
        }
    }

    @Test
    fun categories_areSeeded() {
        runBlocking {
            assertThat(categories.getAll()).hasSize(16)
            assertThat(categories.findById(SystemCategories.OTHER)?.name).isEqualTo("Other")
        }
    }

    private fun sampleTx(
        id: String,
        fingerprint: String,
        amount: Long = 100L,
        timestamp: Long = 1_000L,
    ): Transaction {
        val now = EpochMillis.of(timestamp)
        return Transaction(
            id = TransactionId.of(id),
            sourceMessageHash = "hash-$id",
            fingerprint = TransactionFingerprint.of(fingerprint),
            timestamp = now,
            amount = Money.ofMinorUnits(amount, CurrencyCode.INR),
            merchant = null,
            institution = null,
            maskedAccount = null,
            referenceHash = null,
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.UPI,
            categoryId = SystemCategories.OTHER,
            confidence = Confidence.of(0.9),
            parserVersion = ParserVersion.of("2026.08.10.1"),
            duplicateStatus = DuplicateStatus.NONE,
            possibleDuplicateOf = null,
            transferStatus = TransferStatus.NONE,
            isUserConfirmed = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
