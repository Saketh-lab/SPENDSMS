package com.spendsms.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.spendsms.app.data.room.entity.ParserMetadataEntity

@Dao
interface ParserMetadataDao {

    @Query("SELECT * FROM parser_metadata WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun findActive(): ParserMetadataEntity?

    @Query("SELECT * FROM parser_metadata WHERE parser_version = :parserVersion LIMIT 1")
    suspend fun findByVersion(parserVersion: String): ParserMetadataEntity?

    @Query("SELECT * FROM parser_metadata ORDER BY installed_at DESC")
    suspend fun listInstalled(): List<ParserMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ParserMetadataEntity)

    @Query("UPDATE parser_metadata SET status = 'INSTALLED' WHERE status = 'ACTIVE'")
    suspend fun clearActiveStatus()

    @Transaction
    suspend fun activate(parserVersion: String, activatedAt: Long) {
        clearActiveStatus()
        upsert(
            requireNotNull(findByVersion(parserVersion)).copy(
                status = "ACTIVE",
                activatedAt = activatedAt,
            ),
        )
    }

    @Query(
        """
        UPDATE parser_metadata
        SET status = 'ROLLBACK'
        WHERE parser_version = :parserVersion
        """,
    )
    suspend fun markRollback(parserVersion: String)

    @Query(
        """
        UPDATE parser_metadata
        SET status = 'INVALID', activated_at = NULL
        WHERE parser_version = :parserVersion
        """,
    )
    suspend fun markInvalid(parserVersion: String)
}
