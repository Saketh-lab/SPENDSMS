package com.spendsms.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spendsms.app.data.room.entity.SubscriptionEntity
import com.spendsms.app.data.room.entity.SubscriptionTransactionCrossRef

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions WHERE subscription_id = :subscriptionId LIMIT 1")
    suspend fun findById(subscriptionId: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE merchant_key = :merchantKey LIMIT 1")
    suspend fun findByMerchantKey(merchantKey: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE status = :status ORDER BY updated_at DESC")
    suspend fun findByStatus(status: String): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions ORDER BY updated_at DESC")
    suspend fun listAll(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubscriptionEntity)

    @Update
    suspend fun update(entity: SubscriptionEntity)

    @Query("DELETE FROM subscription_transactions WHERE subscription_id = :subscriptionId")
    suspend fun clearEvidence(subscriptionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(crossRefs: List<SubscriptionTransactionCrossRef>)

    @Query(
        """
        SELECT transaction_id FROM subscription_transactions
        WHERE subscription_id = :subscriptionId
        """,
    )
    suspend fun getEvidenceTransactionIds(subscriptionId: String): List<String>

    @Transaction
    suspend fun upsertWithEvidence(
        entity: SubscriptionEntity,
        evidenceTransactionIds: List<String>,
    ) {
        upsert(entity)
        clearEvidence(entity.subscriptionId)
        if (evidenceTransactionIds.isNotEmpty()) {
            insertEvidence(
                evidenceTransactionIds.map { txId ->
                    SubscriptionTransactionCrossRef(
                        subscriptionId = entity.subscriptionId,
                        transactionId = txId,
                    )
                },
            )
        }
    }

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()
}
