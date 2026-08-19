package com.spendsms.app.application.analysis

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.port.TransactionQuery
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.domain.deduplication.DuplicateDetector
import com.spendsms.app.domain.deduplication.DuplicateOutcome
import com.spendsms.app.domain.deduplication.DuplicatePolicy
import com.spendsms.app.domain.deduplication.TransactionFingerprintFactory
import com.spendsms.app.domain.deduplication.classified
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import io.mockk.mockk
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DuplicateDetectionServiceTest {

    private val policy = DuplicatePolicy.Phase0
    private val factory = TransactionFingerprintFactory(policy)
    private val detector = DuplicateDetector(factory, policy)

    @Test
    fun resolve_repeatedScan_doesNotInsertSecondRow() = runBlocking {
        val repo = InMemoryTransactionRepository()
        val service = service(repo, ids = listOf("tx-a", "tx-should-not-use"))
        val classified = classified(sourceHash = "sms-same")
        val first = service.resolve(classified, EpochMillis.of(1L))
        val second = service.resolve(classified, EpochMillis.of(2L))
        assertThat(first.id.value).isEqualTo("tx-a")
        assertThat(second.id).isEqualTo(first.id)
        assertThat(repo.rows).hasSize(1)
        assertThat(first.duplicateStatus).isEqualTo(DuplicateStatus.NONE)
    }

    @Test
    fun resolve_secondAlertWithSameReference_reusesFingerprintRow() = runBlocking {
        val repo = InMemoryTransactionRepository()
        val service = service(repo, ids = listOf("tx-bank"))
        val bank = classified(sourceHash = "sms-bank", referenceHash = "upi-99", institution = "Bank A")
        val upi = classified(sourceHash = "sms-upi", referenceHash = "upi-99", institution = "UPI")
        service.resolve(bank, EpochMillis.of(1L))
        val reused = service.resolve(upi, EpochMillis.of(2L))
        assertThat(repo.rows).hasSize(1)
        assertThat(reused.id.value).isEqualTo("tx-bank")
        assertThat(factory.fingerprint(bank)).isEqualTo(factory.fingerprint(upi))
    }

    @Test
    fun resolve_nearDuplicate_insertsSuspectedSecondRow() = runBlocking {
        val repo = InMemoryTransactionRepository()
        val service = service(repo, ids = listOf("tx-1", "tx-2"))
        val t0 = policy.fingerprintBucketMillis
        val t1 = t0 + policy.fingerprintBucketMillis
        service.resolve(classified(sourceHash = "sms-1", timestamp = t0, referenceHash = null), EpochMillis.of(t0))
        val second = service.resolve(
            classified(sourceHash = "sms-2", timestamp = t1, referenceHash = null),
            EpochMillis.of(t1),
        )
        assertThat(repo.rows).hasSize(2)
        assertThat(second.duplicateStatus).isEqualTo(DuplicateStatus.SUSPECTED)
        assertThat(second.possibleDuplicateOf?.value).isEqualTo("tx-1")
        assertThat(second.fingerprint).isNotEqualTo(repo.rows.first().fingerprint)
    }

    @Test
    fun evaluate_uniqueWhenNoNeighbors() = runBlocking {
        val repo = InMemoryTransactionRepository()
        val service = service(repo, ids = listOf("tx-1"))
        val result = service.evaluate(classified())
        assertThat(result.outcome).isEqualTo(DuplicateOutcome.InsertUnique)
    }

    @Test
    fun evaluate_exactRematch_skipsNearQuery() = runBlocking {
        val repo = InMemoryTransactionRepository()
        val service = service(repo, ids = listOf("tx-a", "tx-unused"))
        val classified = classified(sourceHash = "sms-same")
        service.resolve(classified, EpochMillis.of(1L))
        val nearAfterInsert = repo.findNearCalls
        assertThat(nearAfterInsert).isEqualTo(1)
        service.resolve(classified, EpochMillis.of(2L))
        assertThat(repo.findNearCalls).isEqualTo(nearAfterInsert)
        assertThat(repo.rows).hasSize(1)
    }

    @Test
    fun evaluate_fingerprintRematch_skipsNearQuery() = runBlocking {
        val repo = InMemoryTransactionRepository()
        val service = service(repo, ids = listOf("tx-bank", "tx-unused"))
        val bank = classified(sourceHash = "sms-bank", referenceHash = "upi-99", institution = "Bank A")
        val upi = classified(sourceHash = "sms-upi", referenceHash = "upi-99", institution = "UPI")
        service.resolve(bank, EpochMillis.of(1L))
        val nearAfterInsert = repo.findNearCalls
        assertThat(nearAfterInsert).isEqualTo(1)
        service.resolve(upi, EpochMillis.of(2L))
        assertThat(repo.findNearCalls).isEqualTo(nearAfterInsert)
        assertThat(repo.rows).hasSize(1)
    }

    private fun service(
        repo: InMemoryTransactionRepository,
        ids: List<String>,
    ): DuplicateDetectionService {
        val iterator = ids.iterator()
        return DuplicateDetectionService(
            transactionRepository = repo,
            detector = detector,
            fingerprintFactory = factory,
            policy = policy,
            userCorrectionRepository = mockk(relaxed = true),
            idFactory = TransactionIdFactory {
                TransactionId.of(iterator.next())
            },
        )
    }
}

private class InMemoryTransactionRepository : TransactionRepository {
    val rows = mutableListOf<Transaction>()
    var findNearCalls: Int = 0

    override suspend fun findById(id: TransactionId): Transaction? =
        rows.find { it.id == id }

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
            val merged = transaction.copy(
                id = rows[index].id,
                createdAt = rows[index].createdAt,
            )
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
    ): List<Transaction> {
        findNearCalls += 1
        return rows.filter {
            it.amount == amount &&
                abs(it.timestamp.toEpochMillis - around.toEpochMillis) <= windowMillis
        }
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
