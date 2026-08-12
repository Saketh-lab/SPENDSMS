package com.spendsms.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendsms.app.data.room.entity.UserCorrectionEntity

@Dao
interface UserCorrectionDao {

    @Query("SELECT * FROM user_corrections WHERE correction_id = :correctionId LIMIT 1")
    suspend fun findById(correctionId: String): UserCorrectionEntity?

    @Query(
        """
        SELECT * FROM user_corrections
        WHERE transaction_id = :transactionId
        ORDER BY created_at ASC
        """,
    )
    suspend fun findByTransactionId(transactionId: String): List<UserCorrectionEntity>

    @Query(
        """
        SELECT * FROM user_corrections
        WHERE apply_to_future = 1
          AND merchant_match_key = :merchantMatchKey
          AND field_name = :fieldName
        ORDER BY created_at DESC
        """,
    )
    suspend fun findFutureRules(
        merchantMatchKey: String,
        fieldName: String,
    ): List<UserCorrectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserCorrectionEntity)

    @Query("DELETE FROM user_corrections WHERE transaction_id = :transactionId")
    suspend fun deleteByTransactionId(transactionId: String)

    @Query("DELETE FROM user_corrections")
    suspend fun deleteAll()
}
