package com.spendsms.app.application.analysis

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserBundleFailureReason
import com.spendsms.app.application.parser.ParserBundleInstallResult
import com.spendsms.app.application.parser.ParserBundleManager
import com.spendsms.app.application.parser.ParserBundleValidationResult
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.TransactionQuery
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.sms.EphemeralSmsMessage
import com.spendsms.app.application.port.sms.SmsAccessException
import com.spendsms.app.application.port.sms.SmsBatch
import com.spendsms.app.application.port.sms.SmsBatchQuery
import com.spendsms.app.application.port.sms.SmsMessageSource
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.data.parser.ParserRulesCodec
import com.spendsms.app.domain.categorisation.CategorisationEngine
import com.spendsms.app.domain.deduplication.DuplicateDetector
import com.spendsms.app.domain.deduplication.DuplicatePolicy
import com.spendsms.app.domain.deduplication.TransactionFingerprintFactory
import com.spendsms.app.domain.filtering.DefaultFinancialMessageFilter
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.merchant.MerchantNormalizer
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.Confidence
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
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionCandidate
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.parsing.ConfidencePolicy
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.DeclarativeTransactionParser
import com.spendsms.app.domain.semantics.TransactionSemantics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test

class ScanCoordinatorTest {

    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(9_000L))
    private val rules = loadRegressionRules()
    private val clock = ScanClock { 1_700_000_000_000L }

    @Test
    fun permissionDenied_failsWithoutReadingSms() = runBlocking {
        val source = mockk<SmsMessageSource>(relaxed = true)
        val coordinator = coordinator(
            source = source,
            permission = SmsPermissionPort { false },
        )
        val result = coordinator.startScan(ScanRequest(period))
        assertThat(result).isInstanceOf(ScanResult.Failed::class.java)
        assertThat((result as ScanResult.Failed).reason).isEqualTo(ScanFailureReason.PERMISSION_DENIED)
        coVerify(exactly = 0) { source.readBatch(any()) }
    }

    @Test
    fun pipeline_parseThenClassifyThenDedupe() = runBlocking {
        val candidate = sampleCandidate()
        val classified = TransactionClassificationService(
            MerchantNormalizer(),
            CategorisationEngine(),
            TransactionSemantics(),
            mockk(relaxed = true),
        ).classifyWithCorrections(candidate)
        val now = EpochMillis.of(1_700_000_000_000L)
        val pipeline = mockk<MessageParsingPipeline>()
        val classifier = mockk<TransactionClassificationService>()
        val dedupe = mockk<DuplicateDetectionService>()
        every { pipeline.process(any(), any()) } returns MessagePipelineResult.Parsed(candidate)
        coEvery { classifier.classify(candidate) } returns classified
        coEvery { dedupe.resolve(classified, now) } returns sampleTransaction("tx-1")

        val source = InMemorySmsMessageSource(
            listOf(sms("1", "EX-BANK", "Rs.10.00 debited at SWIGGY")),
        )
        val coordinator = coordinator(
            source = source,
            pipeline = pipeline,
            classifier = classifier,
            dedupe = dedupe,
        )
        val result = coordinator.startScan(ScanRequest(period, batchSize = 10))
        assertThat(result).isInstanceOf(ScanResult.Completed::class.java)
        coVerifyOrder {
            pipeline.process(any(), any())
            classifier.classify(candidate)
            dedupe.resolve(classified, now)
        }
    }

    @Test
    fun batching_checkpointsAfterEachBatch() = runBlocking {
        val messages = (1..5).map { i ->
            sms(
                id = i.toString(),
                sender = "EX-BANK",
                body = "Rs.${i}0.00 debited at SWIGGY",
                receivedAt = 2_000L + i,
            )
        }
        val scans = InMemoryScanStateRepository()
        val coordinator = coordinator(
            source = InMemorySmsMessageSource(messages),
            scans = scans,
        )
        val checkpoints = mutableListOf<String?>()
        val result = coordinator.startScan(ScanRequest(period, batchSize = 2)) { state ->
            if (state.status == ScanStatus.RUNNING && state.processedCount > 0) {
                checkpoints += state.lastProcessedMessageId
            }
        }
        assertThat(result).isInstanceOf(ScanResult.Completed::class.java)
        val completed = (result as ScanResult.Completed).state
        assertThat(completed.processedCount).isEqualTo(5)
        assertThat(completed.acceptedCount).isEqualTo(5)
        assertThat(checkpoints).containsAtLeast("2", "4", "5")
        assertThat(completed.lastProcessedMessageId).isEqualTo("5")
    }

    @Test
    fun resume_continuesFromCheckpointAfterPermissionRevoke() = runBlocking {
        val permission = MutablePermission(true)
        val source = InMemorySmsMessageSource(
            (1..4).map { i ->
                sms(
                    id = i.toString(),
                    sender = "EX-BANK",
                    body = "Rs.${i}0.00 debited at SWIGGY",
                    receivedAt = 2_000L + i,
                )
            },
        )
        val scans = InMemoryScanStateRepository()
        val txs = ScanTestTransactionRepository()
        val coordinator = coordinator(source = source, permission = permission, scans = scans, txs = txs)
        val first = coordinator.startScan(ScanRequest(period, batchSize = 2)) { state ->
            if (state.processedCount == 2 && state.status == ScanStatus.RUNNING) {
                permission.granted = false
            }
        }
        assertThat(first).isInstanceOf(ScanResult.Interrupted::class.java)
        val interrupted = (first as ScanResult.Interrupted).state
        assertThat(interrupted.lastProcessedMessageId).isEqualTo("2")
        assertThat(interrupted.processedCount).isEqualTo(2)
        assertThat(txs.rows).hasSize(2)

        permission.granted = true
        val resumed = coordinator.startScan(
            ScanRequest(period, resumeScanId = interrupted.id, batchSize = 2),
        )
        assertThat(resumed).isInstanceOf(ScanResult.Completed::class.java)
        val done = (resumed as ScanResult.Completed).state
        assertThat(done.processedCount).isEqualTo(4)
        assertThat(txs.rows).hasSize(4)
    }

    @Test
    fun requestCancel_stopsAndPersistsCancelled() = runBlocking {
        val messages = (1..6).map { i ->
            sms(
                id = i.toString(),
                sender = "EX-BANK",
                body = "Rs.${i}0.00 debited at SWIGGY",
                receivedAt = 2_000L + i,
            )
        }
        val coordinator = coordinator(source = InMemorySmsMessageSource(messages))
        var scanId: ScanId? = null
        val result = coordinator.startScan(ScanRequest(period, batchSize = 1)) { state ->
            scanId = state.id
            if (state.processedCount >= 2) {
                coordinator.requestCancel(state.id)
            }
        }
        assertThat(result).isInstanceOf(ScanResult.Cancelled::class.java)
        val cancelled = (result as ScanResult.Cancelled).state
        assertThat(cancelled.status).isEqualTo(ScanStatus.CANCELLED)
        assertThat(cancelled.processedCount).isAtLeast(2)
        assertThat(cancelled.processedCount).isLessThan(6)
        assertThat(scanId).isEqualTo(cancelled.id)
    }

    @Test
    fun rescan_isIdempotent() = runBlocking {
        val messages = listOf(
            sms("1", "EX-BANK", "Rs.42.00 debited at SWIGGY"),
            sms("2", "EX-BANK", "Your OTP is 123456"),
        )
        val txs = ScanTestTransactionRepository()
        val scans = InMemoryScanStateRepository()
        val ids = ArrayDeque(listOf("scan-a", "scan-b"))
        val coordinator = coordinator(
            source = InMemorySmsMessageSource(messages),
            scans = scans,
            txs = txs,
            scanIds = { ScanId.of(ids.removeFirst()) },
        )
        val first = coordinator.startScan(ScanRequest(period))
        val second = coordinator.startScan(ScanRequest(period))
        assertThat(first).isInstanceOf(ScanResult.Completed::class.java)
        assertThat(second).isInstanceOf(ScanResult.Completed::class.java)
        assertThat(txs.rows).hasSize(1)
        assertThat((first as ScanResult.Completed).state.acceptedCount).isEqualTo(1)
        assertThat((second as ScanResult.Completed).state.acceptedCount).isEqualTo(1)
    }

    @Test
    fun malformedMessage_isIsolated_andScanCompletes() = runBlocking {
        val classifier = mockk<TransactionClassificationService>()
        coEvery { classifier.classify(any()) } throws IllegalStateException("boom")
        val coordinator = coordinator(
            source = InMemorySmsMessageSource(
                listOf(
                    sms("1", "EX-BANK", "Rs.10.00 debited at SWIGGY"),
                    sms("2", "EX-BANK", "Rs.11.00 debited at ZOMATO"),
                ),
            ),
            classifier = classifier,
        )
        val result = coordinator.startScan(ScanRequest(period))
        assertThat(result).isInstanceOf(ScanResult.Completed::class.java)
        val completed = result as ScanResult.Completed
        assertThat(completed.isolatedFailureCount).isEqualTo(2)
        assertThat(completed.state.processedCount).isEqualTo(2)
        assertThat(completed.state.acceptedCount).isEqualTo(0)
        assertThat(completed.state.status).isEqualTo(ScanStatus.COMPLETED)
    }

    @Test
    fun providerError_interruptsAndIsResumable() = runBlocking {
        val source = mockk<SmsMessageSource>()
        coEvery { source.readBatch(any()) } throws
            SmsAccessException.ProviderError("inbox unavailable")
        val coordinator = coordinator(source = source)
        val result = coordinator.startScan(ScanRequest(period))
        assertThat(result).isInstanceOf(ScanResult.Interrupted::class.java)
        assertThat((result as ScanResult.Interrupted).reason)
            .isEqualTo(ScanInterruptReason.PROVIDER_ERROR)
        assertThat(result.state.status).isEqualTo(ScanStatus.INTERRUPTED)
    }

    @Test
    fun otp_isFilteredAndNotPersisted() = runBlocking {
        val txs = ScanTestTransactionRepository()
        val coordinator = coordinator(
            source = InMemorySmsMessageSource(
                listOf(sms("1", "EX-BANK", "Your OTP is 999999. Do not share this OTP")),
            ),
            txs = txs,
        )
        val result = coordinator.startScan(ScanRequest(period))
        assertThat(result).isInstanceOf(ScanResult.Completed::class.java)
        assertThat((result as ScanResult.Completed).state.processedCount).isEqualTo(1)
        assertThat(result.state.acceptedCount).isEqualTo(0)
        assertThat(txs.rows).isEmpty()
    }

    @Test
    fun partialBatchWithoutCheckpoint_resumeDoesNotDuplicate() = runBlocking {
        val txs = ScanTestTransactionRepository()
        val scans = InMemoryScanStateRepository()
        val source = InMemorySmsMessageSource(
            listOf(
                sms("1", "EX-BANK", "Rs.10.00 debited at SWIGGY", receivedAt = 2_001L),
                sms("2", "EX-BANK", "Rs.11.00 debited at SWIGGY", receivedAt = 2_002L),
            ),
        )
        val coordinator = coordinator(source = source, scans = scans, txs = txs)
        val first = coordinator.startScan(ScanRequest(period, batchSize = 10)) as ScanResult.Completed
        // Simulate a crash after persist but with an older checkpoint still stored.
        scans.save(
            first.state.copy(
                status = ScanStatus.INTERRUPTED,
                completedAt = null,
                lastProcessedMessageId = null,
                processedCount = 0,
                acceptedCount = 0,
                updatedAt = EpochMillis.of(clock.nowMillis()),
            ),
        )
        val resumed = coordinator.startScan(
            ScanRequest(period, resumeScanId = first.state.id, batchSize = 10),
        )
        assertThat(resumed).isInstanceOf(ScanResult.Completed::class.java)
        assertThat(txs.rows).hasSize(2)
    }

    @Test
    fun leftoverRunning_withDifferentPeriod_resumesExistingInsteadOfFailing() = runBlocking {
        val messages = listOf(
            sms("1", "EX-BANK", "Rs.10.00 debited at SWIGGY", receivedAt = 2_001L),
            sms("2", "EX-BANK", "Rs.11.00 debited at SWIGGY", receivedAt = 2_002L),
        )
        val txs = ScanTestTransactionRepository()
        val scans = InMemoryScanStateRepository()
        val coordinator = coordinator(
            source = InMemorySmsMessageSource(messages),
            scans = scans,
            txs = txs,
        )
        val first = coordinator.startScan(ScanRequest(period, batchSize = 10)) as ScanResult.Completed
        scans.save(
            first.state.copy(
                status = ScanStatus.RUNNING,
                completedAt = null,
                lastProcessedMessageId = "1",
                processedCount = 1,
                acceptedCount = 1,
                updatedAt = EpochMillis.of(clock.nowMillis()),
            ),
        )
        val otherPeriod = AnalysisPeriod(EpochMillis.of(10_000L), EpochMillis.of(20_000L))
        val result = coordinator.startScan(ScanRequest(otherPeriod))
        assertThat(result).isInstanceOf(ScanResult.Completed::class.java)
        assertThat((result as ScanResult.Completed).state.id).isEqualTo(first.state.id)
        assertThat(txs.rows).hasSize(2)
    }

    private fun coordinator(
        source: SmsMessageSource,
        permission: SmsPermissionPort = SmsPermissionPort { true },
        scans: InMemoryScanStateRepository = InMemoryScanStateRepository(),
        txs: ScanTestTransactionRepository = ScanTestTransactionRepository(),
        pipeline: MessageParsingPipeline = MessageParsingPipeline(
            DefaultFinancialMessageFilter(),
            DeclarativeTransactionParser(ConfidencePolicy.Phase0),
        ),
        classifier: TransactionClassificationService = TransactionClassificationService(
            MerchantNormalizer(),
            CategorisationEngine(),
            TransactionSemantics(),
            mockk(relaxed = true),
        ),
        dedupe: DuplicateDetectionService? = null,
        scanIds: () -> ScanId = { ScanId.of("scan-1") },
    ): ScanCoordinator {
        val policy = DuplicatePolicy.Phase0
        val factory = TransactionFingerprintFactory(policy)
        val detection = dedupe ?: DuplicateDetectionService(
            transactionRepository = txs,
            detector = DuplicateDetector(factory, policy),
            fingerprintFactory = factory,
            policy = policy,
            userCorrectionRepository = mockk(relaxed = true),
            idFactory = TransactionIdFactory { TransactionId.of("tx-${txs.rows.size + 1}") },
        )
        return ScanCoordinator(
            smsSource = source,
            permissionPort = permission,
            parserBundleManager = FakeParserBundleManager(rules),
            controlPlane = controlPlaneFor(rules),
            parsingPipeline = pipeline,
            classificationService = classifier,
            duplicateDetectionService = detection,
            scanStateRepository = scans,
            scanIdFactory = ScanIdFactory { scanIds() },
            clock = clock,
        )
    }

    private fun controlPlaneFor(
        rules: DeclarativeParserRules,
    ): ControlPlaneCoordinator {
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

    private fun sms(
        id: String,
        sender: String,
        body: String,
        receivedAt: Long = 2_000L,
    ): EphemeralSmsMessage =
        EphemeralSmsMessage(
            sourceMessageId = id,
            sender = sender,
            receivedAt = EpochMillis.of(receivedAt),
            body = body,
        )

    private fun sampleCandidate(): TransactionCandidate = TransactionCandidate(
        sourceMessageHash = "hash-1",
        amount = Money.ofMinorUnits(1_000L, CurrencyCode.INR),
        transactionTimestamp = EpochMillis.of(2_000L),
        merchantRaw = "SWIGGY",
        institution = "Example Bank",
        maskedAccount = null,
        direction = TransactionDirection.DEBIT,
        paymentMethod = PaymentMethod.UPI,
        referenceHash = null,
        confidence = Confidence.of(0.9),
        parserVersion = ParserVersion.of("regression-2026.08.12.0"),
    )

    private fun sampleTransaction(id: String): Transaction = Transaction(
        id = TransactionId.of(id),
        sourceMessageHash = "hash-1",
        fingerprint = TransactionFingerprint.of("fp-1"),
        timestamp = EpochMillis.of(2_000L),
        amount = Money.ofMinorUnits(1_000L, CurrencyCode.INR),
        merchant = Merchant(MerchantKey.of("swiggy"), "Swiggy", "SWIGGY"),
        institution = "Example Bank",
        maskedAccount = null,
        referenceHash = null,
        direction = TransactionDirection.DEBIT,
        paymentMethod = PaymentMethod.UPI,
        categoryId = SystemCategories.FOOD_AND_DINING,
        confidence = Confidence.of(0.9),
        parserVersion = ParserVersion.of("regression-2026.08.12.0"),
        duplicateStatus = DuplicateStatus.NONE,
        possibleDuplicateOf = null,
        transferStatus = TransferStatus.NONE,
        isUserConfirmed = false,
        createdAt = EpochMillis.of(clock.nowMillis()),
        updatedAt = EpochMillis.of(clock.nowMillis()),
    )
}

private fun loadRegressionRules(): DeclarativeParserRules {
    val codec = ParserRulesCodec(
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = false
        },
    )
    val utf8 = checkNotNull(
        ScanCoordinatorTest::class.java.classLoader!!
            .getResourceAsStream("parser/regression_rules.json"),
    ).bufferedReader().readText()
    return codec.decodeRules(utf8)
}

private class MutablePermission(var granted: Boolean) : SmsPermissionPort {
    override fun hasReadSmsPermission(): Boolean = granted
}

private class InMemorySmsMessageSource(
    private val messages: List<EphemeralSmsMessage>,
) : SmsMessageSource {
    var beforeRead: (Int) -> Unit = {}
    private var reads = 0

    override suspend fun readBatch(query: SmsBatchQuery): SmsBatch {
        reads += 1
        beforeRead(reads)
        val after = query.afterMessageId?.toLongOrNull() ?: -1L
        val inPeriod = messages.filter {
            it.receivedAt.toEpochMillis in
                query.period.start.toEpochMillis..query.period.end.toEpochMillis
        }.sortedBy { it.sourceMessageId.toLong() }
        val slice = inPeriod.filter { it.sourceMessageId.toLong() > after }.take(query.limit)
        return SmsBatch(
            messages = slice,
            nextAfterMessageId = slice.lastOrNull()?.sourceMessageId,
            exhausted = slice.size < query.limit,
        )
    }
}

private class InMemoryScanStateRepository : ScanStateRepository {
    private val rows = mutableMapOf<String, ScanState>()

    override suspend fun findById(id: ScanId): ScanState? = rows[id.value]

    override suspend fun findActive(): ScanState? =
        rows.values.firstOrNull { it.status == ScanStatus.PENDING || it.status == ScanStatus.RUNNING }

    override suspend fun findResumable(): ScanState? =
        rows.values
            .filter {
                it.status == ScanStatus.PENDING ||
                    it.status == ScanStatus.RUNNING ||
                    it.status == ScanStatus.INTERRUPTED
            }
            .maxByOrNull { it.updatedAt.toEpochMillis }

    override suspend fun findLatestCompleted(): ScanState? =
        rows.values.filter { it.status == ScanStatus.COMPLETED }.maxByOrNull { it.updatedAt.toEpochMillis }

    override suspend fun save(state: ScanState): ScanState {
        rows[state.id.value] = state
        return state
    }

    override suspend fun deleteAll() {
        rows.clear()
    }
}

private class ScanTestTransactionRepository : TransactionRepository {
    val rows = mutableListOf<Transaction>()

    override suspend fun findById(id: TransactionId): Transaction? = rows.find { it.id == id }

    override suspend fun findByFingerprint(fingerprint: TransactionFingerprint): Transaction? =
        rows.find { it.fingerprint == fingerprint }

    override suspend fun findBySourceMessageHash(sourceMessageHash: String): Transaction? =
        rows.find { it.sourceMessageHash == sourceMessageHash }

    override suspend fun upsert(transaction: Transaction): Transaction {
        val index = rows.indexOfFirst { it.fingerprint == transaction.fingerprint }
        return if (index < 0) {
            rows += transaction
            transaction
        } else {
            val merged = transaction.copy(id = rows[index].id, createdAt = rows[index].createdAt)
            rows[index] = merged
            merged
        }
    }

    override suspend fun upsertAll(transactions: List<Transaction>) {
        transactions.forEach { upsert(it) }
    }

    override suspend fun update(transaction: Transaction): Transaction {
        val index = rows.indexOfFirst { it.id == transaction.id }
        if (index >= 0) rows[index] = transaction
        return transaction
    }

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

    override suspend fun query(query: TransactionQuery): List<Transaction> = rows

    override suspend fun deleteAllAnalysed() {
        rows.clear()
    }
}

private class FakeParserBundleManager(
    private val rules: DeclarativeParserRules,
) : ParserBundleManager {
    override fun validateSignedBundle(bundleJsonUtf8: String): ParserBundleValidationResult =
        ParserBundleValidationResult.Invalid(ParserBundleFailureReason.MALFORMED_JSON, "unused")

    override suspend fun installVerifiedBundle(bundleJsonUtf8: String): ParserBundleInstallResult =
        ParserBundleInstallResult.Rejected(ParserBundleFailureReason.MALFORMED_JSON, "unused")

    override suspend fun activateVerified(version: ParserVersion): ParserBundleActivationResult =
        activated()

    override suspend fun ensureActiveOrFallbackToBundled(): ParserBundleActivationResult = activated()

    override suspend fun loadActiveRules(): DeclarativeParserRules = rules

    private fun activated() = ParserBundleActivationResult.Activated(
        metadata = ParserMetadata(
            parserVersion = rules.parserVersion,
            rulesVersion = RulesVersion.of("regression-rules-1"),
            schemaVersion = 1,
            checksum = "abc",
            installedAt = EpochMillis.of(1L),
            activatedAt = EpochMillis.of(1L),
            status = ParserBundleStatus.ACTIVE,
        ),
        rules = rules,
        usedBundledFallback = true,
    )
}
