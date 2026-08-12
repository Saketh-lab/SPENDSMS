package com.spendsms.app.application.port

import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId

/**
 * Local financial transaction store — sole system-of-record boundary for
 * normalised transactions (Step-3). Implementations must never persist raw SMS.
 */
interface TransactionRepository {

    suspend fun findById(id: TransactionId): Transaction?

    suspend fun findByFingerprint(fingerprint: TransactionFingerprint): Transaction?

    suspend fun findBySourceMessageHash(sourceMessageHash: String): Transaction?

    /**
     * Insert or update by [Transaction.fingerprint] while preserving caller-supplied
     * effective values (corrections are applied by use cases before upsert).
     */
    suspend fun upsert(transaction: Transaction): Transaction

    suspend fun upsertAll(transactions: List<Transaction>)

    suspend fun update(transaction: Transaction): Transaction

    suspend fun findInPeriod(period: AnalysisPeriod): List<Transaction>

    /**
     * Nearby rows used by duplicate detection (amount + timestamp window).
     */
    suspend fun findNear(
        amount: Money,
        around: EpochMillis,
        windowMillis: Long,
    ): List<Transaction>

    suspend fun findByMerchantKey(
        merchantKey: MerchantKey,
        period: AnalysisPeriod? = null,
    ): List<Transaction>

    suspend fun query(query: TransactionQuery): List<Transaction>

    suspend fun deleteAllAnalysed()
}

/**
 * Capability-focused read query for transaction list / review screens.
 */
data class TransactionQuery(
    val period: AnalysisPeriod? = null,
    val categoryId: CategoryId? = null,
    val direction: TransactionDirection? = null,
    val paymentMethod: PaymentMethod? = null,
    val merchantSearch: String? = null,
    val limit: Int? = null,
)
