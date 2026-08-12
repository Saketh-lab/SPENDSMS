package com.spendsms.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendsms.app.data.room.entity.ScanStateEntity

@Dao
interface ScanStateDao {

    @Query("SELECT * FROM scan_state WHERE scan_id = :scanId LIMIT 1")
    suspend fun findById(scanId: String): ScanStateEntity?

    @Query(
        """
        SELECT * FROM scan_state
        WHERE status IN ('PENDING', 'RUNNING')
        ORDER BY updated_at DESC
        LIMIT 1
        """,
    )
    suspend fun findActive(): ScanStateEntity?

    @Query(
        """
        SELECT * FROM scan_state
        WHERE status = 'COMPLETED'
        ORDER BY completed_at DESC, updated_at DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestCompleted(): ScanStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScanStateEntity)

    @Query("DELETE FROM scan_state")
    suspend fun deleteAll()
}
