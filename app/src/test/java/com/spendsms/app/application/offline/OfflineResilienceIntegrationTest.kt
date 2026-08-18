package com.spendsms.app.application.offline

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.analysis.ScanCompletionHandler
import com.spendsms.app.application.controlplane.ControlPlaneBootstrapResult
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.corrections.ApplyCorrectionRequest
import com.spendsms.app.application.corrections.ApplyCorrectionResult
import com.spendsms.app.application.corrections.CorrectionIdFactory
import com.spendsms.app.application.corrections.UserCorrectionService
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserBundleFailureReason
import com.spendsms.app.application.parser.ParserUpdateService
import com.spendsms.app.application.port.config.AppRemoteConfig
import com.spendsms.app.data.config.BundledRemoteConfigPort
import com.spendsms.app.data.config.ControlPlaneEndpointConfig
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.data.repository.RoomDashboardCachePort
import com.spendsms.app.data.repository.RoomFinancialDataDeletionPort
import com.spendsms.app.data.repository.RoomScanStateRepository
import com.spendsms.app.data.repository.RoomSubscriptionRepository
import com.spendsms.app.data.repository.RoomTransactionRepository
import com.spendsms.app.data.repository.RoomUserCorrectionRepository
import com.spendsms.app.data.room.SpendSmsDatabase
import com.spendsms.app.data.support.UnavailableSupportSubmissionPort
import com.spendsms.app.data.telemetry.NoOpTelemetryPort
import com.spendsms.app.data.telemetry.SilentTelemetryRecorder
import com.spendsms.app.domain.dashboard.DashboardCalculator
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
import com.spendsms.app.domain.model.ScanStatus
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.TransactionSemantics
import com.spendsms.app.application.subscriptions.SubscriptionDetectionService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cross-layer offline journeys: Room is SoR, cloud absence never blocks, scans/dashboard stay local.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineResilienceIntegrationTest {

    private lateinit var db: SpendSmsDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var scans: RoomScanStateRepository
    private lateinit var dashboard: DashboardService
    private val period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(9_999L))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = SpendSmsDatabase.buildInMemory(context)
        transactions = RoomTransactionRepository(db, db.transactionDao())
        scans = RoomScanStateRepository(db.scanStateDao())
        dashboard = DashboardService(
            transactionRepository = transactions,
            subscriptionRepository = RoomSubscriptionRepository(db.subscriptionDao()),
            userCorrectionRepository = RoomUserCorrectionRepository(db.userCorrectionDao()),
            scanStateRepository = scans,
            dashboardCachePort = RoomDashboardCachePort(
                dashboardCacheDao = db.dashboardCacheDao(),
                transactionDao = db.transactionDao(),
                json = Json { ignoreUnknownKeys = false },
            ),
            calculator = DashboardCalculator(TransactionSemantics()),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun scanCompletion_recomputesDashboardFromRoom() = runBlocking {
        transactions.upsert(sampleTx("tx-1", "fp-1", amount = 500L))
        scans.save(
            ScanState(
                id = ScanId.of("scan-1"),
                period = period,
                lastProcessedMessageId = "1",
                parserVersion = ParserVersion.of("bundled-2026.08.12.0"),
                status = ScanStatus.COMPLETED,
                processedCount = 1,
                acceptedCount = 1,
                startedAt = EpochMillis.of(1L),
                completedAt = EpochMillis.of(2L),
                updatedAt = EpochMillis.of(2L),
            ),
        )
        val preferences = mockk<UserPreferencesStore>(relaxed = true)
        val subscriptions = mockk<SubscriptionDetectionService>(relaxed = true)
        val handler = ScanCompletionHandler(preferences, subscriptions, dashboard)

        handler.onScanCompleted(period, EpochMillis.of(2L))

        coVerify { preferences.setLastAnalysisPeriod(period) }
        coVerify { subscriptions.detectAndPersist(period, EpochMillis.of(2L)) }
        val cached = dashboard.getDashboard(period, useCache = true)
        assertThat(cached.transactionCount).isEqualTo(1)
        assertThat(cached.grossSpending.amountMinorUnits).isEqualTo(500L)
    }

    @Test
    fun repeatedUpsert_doesNotCreateDuplicateFinancialRows() = runBlocking {
        val first = transactions.upsert(sampleTx("tx-1", "fp-same", amount = 100L))
        val second = transactions.upsert(sampleTx("tx-2", "fp-same", amount = 100L))
        assertThat(second.id).isEqualTo(first.id)
        assertThat(transactions.findInPeriod(period)).hasSize(1)
    }

    @Test
    fun interruptedScanCheckpoint_isResumableAfterSimulatedProcessDeath() = runBlocking {
        scans.save(
            ScanState(
                id = ScanId.of("alive"),
                period = period,
                lastProcessedMessageId = "40",
                parserVersion = ParserVersion.of("bundled-2026.08.12.0"),
                status = ScanStatus.RUNNING,
                processedCount = 40,
                acceptedCount = 12,
                startedAt = EpochMillis.of(1L),
                completedAt = null,
                updatedAt = EpochMillis.of(2L),
            ),
        )
        assertThat(scans.findResumable()?.lastProcessedMessageId).isEqualTo("40")
        assertThat(scans.findLatestCompleted()).isNull()
    }

    @Test
    fun localOnlyControlPlane_bootstrapNeverChecksRemoteParser() = runBlocking {
        val endpoints = ControlPlaneEndpointConfig()
        assertThat(endpoints.isLocalOnlyMode).isTrue()
        val parser = mockk<ParserUpdateService>()
        coEvery { parser.ensureBundledParserReady() } returns ParserBundleActivationResult.Failed(
            reason = ParserBundleFailureReason.BUNDLED_FALLBACK_UNAVAILABLE,
            detail = "unused",
        )
        val coordinator = ControlPlaneCoordinator(
            endpoints = endpoints,
            remoteConfig = BundledRemoteConfigPort(endpoints),
            parserUpdateService = parser,
            supportSubmissionPort = UnavailableSupportSubmissionPort(),
            telemetry = SilentTelemetryRecorder(NoOpTelemetryPort()),
        )
        val status = coordinator.currentStatus()
        assertThat(status.isLocalOnlyMode).isTrue()
        assertThat(status.parserUpdatesEnabled).isFalse()
        assertThat(status.supportSubmissionEnabled).isFalse()
        val result = coordinator.bootstrap()
        assertThat(
            result is ControlPlaneBootstrapResult.Ready ||
                result is ControlPlaneBootstrapResult.ParserDegraded,
        ).isTrue()
        coVerify(exactly = 0) { parser.checkAndApplyUpdate(any()) }
        assertThat(AppRemoteConfig.localOnlyDefaults().newParserVersionEnabled).isFalse()
    }

    @Test
    fun correction_notATransaction_isReflectedOnDashboard() = runBlocking {
        transactions.upsert(sampleTx("tx-1", "fp-1", amount = 500L))
        dashboard.recomputeAndCache(period)
        assertThat(dashboard.getDashboard(period, useCache = true).transactionCount).isEqualTo(1)

        val service = UserCorrectionService(
            transactionRepository = transactions,
            userCorrectionRepository = RoomUserCorrectionRepository(db.userCorrectionDao()),
            subscriptionRepository = RoomSubscriptionRepository(db.subscriptionDao()),
            merchantNormalizer = MerchantNormalizer(),
            semantics = TransactionSemantics(),
            dashboardService = dashboard,
            subscriptionDetectionService = mockk(relaxed = true),
            correctionIdFactory = CorrectionIdFactory { CorrectionId.of("c-1") },
        )
        val result = service.apply(
            ApplyCorrectionRequest(
                transactionId = TransactionId.of("tx-1"),
                field = CorrectionField.NOT_A_TRANSACTION,
                newValue = "true",
                dashboardPeriod = period,
            ),
            now = EpochMillis.of(2_000L),
        )
        assertThat(result).isInstanceOf(ApplyCorrectionResult.Applied::class.java)
        val after = dashboard.getDashboard(period, useCache = true)
        assertThat(after.grossSpending.amountMinorUnits).isEqualTo(0L)
        assertThat(
            RoomUserCorrectionRepository(db.userCorrectionDao())
                .findByTransactionId(TransactionId.of("tx-1"))
                .any { it.field == CorrectionField.NOT_A_TRANSACTION },
        ).isTrue()
    }

    @Test
    fun financialDeletion_leavesEmptyDashboardAndNoResumableScan() = runBlocking {
        transactions.upsert(sampleTx("tx-1", "fp-1", amount = 500L))
        scans.save(
            ScanState(
                id = ScanId.of("scan-1"),
                period = period,
                lastProcessedMessageId = "1",
                parserVersion = ParserVersion.of("bundled-2026.08.12.0"),
                status = ScanStatus.INTERRUPTED,
                processedCount = 1,
                acceptedCount = 1,
                startedAt = EpochMillis.of(1L),
                completedAt = null,
                updatedAt = EpochMillis.of(2L),
            ),
        )
        dashboard.recomputeAndCache(period)
        RoomFinancialDataDeletionPort(
            db = db,
            subscriptionDao = db.subscriptionDao(),
            userCorrectionDao = db.userCorrectionDao(),
            transactionDao = db.transactionDao(),
            scanStateDao = db.scanStateDao(),
            dashboardCacheDao = db.dashboardCacheDao(),
        ).deleteAllAnalysedData()
        dashboard.invalidateCache()

        val after = dashboard.getDashboard(period, useCache = false)
        assertThat(after.transactionCount).isEqualTo(0)
        assertThat(after.lastAnalysisAt).isNull()
        assertThat(transactions.findInPeriod(period)).isEmpty()
        assertThat(scans.findResumable()).isNull()
        assertThat(scans.findLatestCompleted()).isNull()
    }

    private fun sampleTx(
        id: String,
        fingerprint: String,
        amount: Long,
    ): Transaction {
        val now = EpochMillis.of(1_000L)
        return Transaction(
            id = TransactionId.of(id),
            sourceMessageHash = "hash-$id",
            fingerprint = TransactionFingerprint.of(fingerprint),
            timestamp = now,
            amount = Money.ofMinorUnits(amount, CurrencyCode.INR),
            merchant = Merchant(MerchantKey.of("cafe"), "Cafe", "CAFE"),
            institution = "Example Bank",
            maskedAccount = null,
            referenceHash = null,
            direction = TransactionDirection.DEBIT,
            paymentMethod = PaymentMethod.UPI,
            categoryId = SystemCategories.FOOD_AND_DINING,
            confidence = Confidence.of(0.9),
            parserVersion = ParserVersion.of("bundled-2026.08.12.0"),
            duplicateStatus = DuplicateStatus.NONE,
            possibleDuplicateOf = null,
            transferStatus = TransferStatus.NONE,
            isUserConfirmed = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
