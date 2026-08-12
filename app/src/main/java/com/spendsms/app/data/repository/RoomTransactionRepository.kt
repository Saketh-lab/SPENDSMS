package com.spendsms.app.data.repository

import androidx.room.withTransaction
import com.spendsms.app.application.port.TransactionQuery
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.data.room.SpendSmsDatabase
import com.spendsms.app.data.room.dao.TransactionDao
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.data.room.mapper.toEntity
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTransactionRepository @Inject constructor(
    private val db: SpendSmsDatabase,
    private val transactionDao: TransactionDao,
) : TransactionRepository {

    override suspend fun findById(id: TransactionId): Transaction? =
        transactionDao.findById(id.value)?.toDomain()

    override suspend fun findByFingerprint(fingerprint: TransactionFingerprint): Transaction? =
        transactionDao.findByFingerprint(fingerprint.value)?.toDomain()

    override suspend fun findBySourceMessageHash(sourceMessageHash: String): Transaction? =
        transactionDao.findBySourceMessageHash(sourceMessageHash)?.toDomain()

    override suspend fun upsert(transaction: Transaction): Transaction {
        val existing = transactionDao.findByFingerprint(transaction.fingerprint.value)
        return if (existing == null) {
            transactionDao.insert(transaction.toEntity())
            transaction
        } else {
            val merged = transaction.copy(
                id = TransactionId.of(existing.transactionId),
                createdAt = EpochMillis.of(existing.createdAt),
            )
            transactionDao.update(merged.toEntity())
            merged
        }
    }

    override suspend fun upsertAll(transactions: List<Transaction>) {
        db.withTransaction {
            transactions.forEach { upsert(it) }
        }
    }

    override suspend fun update(transaction: Transaction): Transaction {
        transactionDao.update(transaction.toEntity())
        return transaction
    }

    override suspend fun findInPeriod(period: AnalysisPeriod): List<Transaction> =
        transactionDao.findInPeriod(
            startInclusive = period.start.toEpochMillis,
            endInclusive = period.end.toEpochMillis,
        ).map { it.toDomain() }

    override suspend fun findNear(
        amount: Money,
        around: EpochMillis,
        windowMillis: Long,
    ): List<Transaction> {
        require(windowMillis >= 0L) { "windowMillis must be >= 0" }
        val start = (around.toEpochMillis - windowMillis).coerceAtLeast(0L)
        val end = around.toEpochMillis + windowMillis
        return transactionDao.findNear(
            amountMinorUnits = amount.amountMinorUnits,
            currency = amount.currency.code,
            windowStart = start,
            windowEnd = end,
        ).map { it.toDomain() }
    }

    override suspend fun findByMerchantKey(
        merchantKey: MerchantKey,
        period: AnalysisPeriod?,
    ): List<Transaction> =
        transactionDao.findByMerchantKey(
            merchantKey = merchantKey.value,
            startInclusive = period?.start?.toEpochMillis,
            endInclusive = period?.end?.toEpochMillis,
        ).map { it.toDomain() }

    override suspend fun query(query: TransactionQuery): List<Transaction> =
        transactionDao.query(
            startInclusive = query.period?.start?.toEpochMillis,
            endInclusive = query.period?.end?.toEpochMillis,
            categoryId = query.categoryId?.value,
            direction = query.direction?.name,
            paymentMethod = query.paymentMethod?.name,
            merchantSearch = query.merchantSearch,
            limit = query.limit ?: Int.MAX_VALUE,
        ).map { it.toDomain() }

    override suspend fun deleteAllAnalysed() {
        transactionDao.deleteAll()
    }
}
