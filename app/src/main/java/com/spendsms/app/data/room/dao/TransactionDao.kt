package com.spendsms.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spendsms.app.data.room.entity.TransactionEntity

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun findById(transactionId: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE transaction_fingerprint = :fingerprint
        LIMIT 1
        """,
    )
    suspend fun findByFingerprint(fingerprint: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE source_message_hash = :sourceMessageHash
        LIMIT 1
        """,
    )
    suspend fun findBySourceMessageHash(sourceMessageHash: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query(
        """
        SELECT * FROM transactions
        WHERE transaction_timestamp BETWEEN :startInclusive AND :endInclusive
        ORDER BY transaction_timestamp DESC
        """,
    )
    suspend fun findInPeriod(startInclusive: Long, endInclusive: Long): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE amount_minor_units = :amountMinorUnits
          AND currency = :currency
          AND transaction_timestamp BETWEEN :windowStart AND :windowEnd
        ORDER BY transaction_timestamp DESC
        """,
    )
    suspend fun findNear(
        amountMinorUnits: Long,
        currency: String,
        windowStart: Long,
        windowEnd: Long,
    ): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE merchant_key = :merchantKey
          AND (:startInclusive IS NULL OR transaction_timestamp >= :startInclusive)
          AND (:endInclusive IS NULL OR transaction_timestamp <= :endInclusive)
        ORDER BY transaction_timestamp DESC
        """,
    )
    suspend fun findByMerchantKey(
        merchantKey: String,
        startInclusive: Long?,
        endInclusive: Long?,
    ): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:startInclusive IS NULL OR transaction_timestamp >= :startInclusive)
          AND (:endInclusive IS NULL OR transaction_timestamp <= :endInclusive)
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:direction IS NULL OR direction = :direction)
          AND (:paymentMethod IS NULL OR payment_method = :paymentMethod)
          AND (
            :merchantSearch IS NULL OR :merchantSearch = ''
            OR merchant_display_name LIKE '%' || :merchantSearch || '%'
            OR merchant_key LIKE '%' || :merchantSearch || '%'
          )
        ORDER BY transaction_timestamp DESC
        LIMIT :limit
        """,
    )
    suspend fun query(
        startInclusive: Long?,
        endInclusive: Long?,
        categoryId: String?,
        direction: String?,
        paymentMethod: String?,
        merchantSearch: String?,
        limit: Int,
    ): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
