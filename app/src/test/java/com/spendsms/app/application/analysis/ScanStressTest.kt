package com.spendsms.app.application.analysis

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.port.sms.EphemeralSmsMessage
import com.spendsms.app.application.port.sms.SmsBatch
import com.spendsms.app.application.port.sms.SmsBatchQuery
import com.spendsms.app.application.port.sms.SmsMessageSource
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.domain.categorisation.CategorisationEngine
import com.spendsms.app.domain.deduplication.DuplicateDetector
import com.spendsms.app.domain.deduplication.DuplicatePolicy
import com.spendsms.app.domain.deduplication.TransactionFingerprintFactory
import com.spendsms.app.domain.filtering.DefaultFinancialMessageFilter
import com.spendsms.app.domain.merchant.MerchantNormalizer
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserBundleStatus
import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanStatus
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.parsing.ConfidencePolicy
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.DeclarativeTransactionParser
import com.spendsms.app.domain.semantics.TransactionSemantics
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Synthetic large-inbox stress coverage for Prompt 19 (local scan pipeline).
 *
 * Budgets are generous for CI VMs; they still fail if batching/caching regresses
 * into multi-minute scans. Raw SMS stays in the ephemeral batch only.
 */
class ScanStressTest {

    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(50_000L))
    private val rules = loadRegressionRules()
    private val clock = ScanClock { 1_700_000_000_000L }

    @Test
    fun largeInbox_processesInBoundedBatches_andRescanIsIdempotent() = runBlocking {
        val financialCount = 1_500
        val otpCount = 500
        val source = RecordingSmsSource(syntheticInbox(financialCount, otpCount))
        val txs = ScanTestTransactionRepository()
        val coordinator = coordinator(source = source, txs = txs)

        val started = System.nanoTime()
        val first = coordinator.startScan(ScanRequest(period, batchSize = DEFAULT_SCAN_BATCH_SIZE))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertThat(first).isInstanceOf(ScanResult.Completed::class.java)
        val completed = first as ScanResult.Completed
        assertThat(completed.state.processedCount).isEqualTo(financialCount + otpCount)
        assertThat(completed.state.acceptedCount).isEqualTo(financialCount)
        assertThat(txs.rows).hasSize(financialCount)
        assertThat(source.observedLimits).isNotEmpty()
        assertThat(source.observedLimits.max()).isAtMost(MAX_SCAN_BATCH_SIZE)
        assertThat(source.observedLimits.toSet()).containsExactly(DEFAULT_SCAN_BATCH_SIZE)
        assertThat(elapsedMs).isLessThan(45_000L)

        val rescan = coordinator.startScan(ScanRequest(period, batchSize = DEFAULT_SCAN_BATCH_SIZE))
        assertThat(rescan).isInstanceOf(ScanResult.Completed::class.java)
        assertThat((rescan as ScanResult.Completed).state.acceptedCount).isEqualTo(financialCount)
        assertThat(txs.rows).hasSize(financialCount)
    }

    @Test
    fun cancel_isResponsiveOnLargeInbox_andResumeCompletes() = runBlocking {
        val financialCount = 800
        val source = RecordingSmsSource(syntheticInbox(financialCount, otpCount = 0))
        val txs = ScanTestTransactionRepository()
        val scans = InMemoryScanStateRepository()
        val coordinator = coordinator(source = source, txs = txs, scans = scans)

        val started = System.nanoTime()
        val cancelled = coordinator.startScan(
            ScanRequest(period, batchSize = 50),
        ) { state ->
            if (state.processedCount >= 50 && state.status == ScanStatus.RUNNING) {
                coordinator.requestCancel(state.id)
            }
        }
        val cancelElapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertThat(cancelled).isInstanceOf(ScanResult.Cancelled::class.java)
        val cancelledState = (cancelled as ScanResult.Cancelled).state
        assertThat(cancelledState.status).isEqualTo(ScanStatus.CANCELLED)
        assertThat(cancelledState.processedCount).isAtLeast(50)
        assertThat(cancelledState.processedCount).isLessThan(financialCount)
        assertThat(cancelElapsedMs).isLessThan(8_000L)

        val restarted = coordinator.startScan(ScanRequest(period, batchSize = 50))
        assertThat(restarted).isInstanceOf(ScanResult.Completed::class.java)
        val done = restarted as ScanResult.Completed
        assertThat(done.state.processedCount).isEqualTo(financialCount)
        assertThat(done.state.acceptedCount).isEqualTo(financialCount)
        assertThat(txs.rows).hasSize(financialCount)
    }

    private fun coordinator(
        source: SmsMessageSource,
        txs: ScanTestTransactionRepository = ScanTestTransactionRepository(),
        scans: InMemoryScanStateRepository = InMemoryScanStateRepository(),
    ): ScanCoordinator {
        val policy = DuplicatePolicy.Phase0
        val factory = TransactionFingerprintFactory(policy)
        return ScanCoordinator(
            smsSource = source,
            permissionPort = SmsPermissionPort { true },
            parserBundleManager = FakeParserBundleManager(rules),
            controlPlane = controlPlaneFor(rules),
            parsingPipeline = MessageParsingPipeline(
                DefaultFinancialMessageFilter(),
                DeclarativeTransactionParser(ConfidencePolicy.Phase0),
            ),
            classificationService = TransactionClassificationService(
                MerchantNormalizer(),
                CategorisationEngine(),
                TransactionSemantics(),
                mockk(relaxed = true),
            ),
            duplicateDetectionService = DuplicateDetectionService(
                transactionRepository = txs,
                detector = DuplicateDetector(factory, policy),
                fingerprintFactory = factory,
                policy = policy,
                userCorrectionRepository = mockk(relaxed = true),
                idFactory = TransactionIdFactory { TransactionId.of("tx-${txs.rows.size + 1}") },
            ),
            scanStateRepository = scans,
            scanIdFactory = ScanIdFactory { ScanId.of("scan-stress") },
            clock = clock,
        )
    }

    private fun controlPlaneFor(rules: DeclarativeParserRules): ControlPlaneCoordinator {
        val metadata = ParserMetadata(
            parserVersion = rules.parserVersion,
            rulesVersion = RulesVersion.of("bundled-rules-1"),
            schemaVersion = rules.schemaVersion,
            checksum = "abc",
            installedAt = EpochMillis.of(1L),
            activatedAt = EpochMillis.of(1L),
            status = ParserBundleStatus.ACTIVE,
        )
        val controlPlane = mockk<ControlPlaneCoordinator>(relaxed = true)
        coEvery { controlPlane.ensureParserReadyForScan() } returns ParserBundleActivationResult.Activated(
            metadata = metadata,
            rules = rules,
            usedBundledFallback = true,
        )
        return controlPlane
    }

    private fun syntheticInbox(financialCount: Int, otpCount: Int): List<EphemeralSmsMessage> {
        val financial = (1..financialCount).map { i ->
            EphemeralSmsMessage(
                sourceMessageId = i.toString(),
                sender = "EX-BANK",
                receivedAt = EpochMillis.of(2_000L + i),
                body = "Rs.$i.00 debited at SWIGGY on 10-08-2026.",
            )
        }
        val otp = (1..otpCount).map { i ->
            val id = financialCount + i
            EphemeralSmsMessage(
                sourceMessageId = id.toString(),
                sender = "VM-OTP",
                receivedAt = EpochMillis.of(2_000L + id),
                body = "Your OTP is ${100000 + i}. Do not share this OTP with anyone.",
            )
        }
        return financial + otp
    }

    private class RecordingSmsSource(
        messages: List<EphemeralSmsMessage>,
    ) : SmsMessageSource {
        private val inner = InMemorySmsMessageSource(messages)
        val observedLimits = mutableListOf<Int>()

        override suspend fun readBatch(query: SmsBatchQuery): SmsBatch {
            observedLimits += query.limit
            return inner.readBatch(query)
        }
    }
}
