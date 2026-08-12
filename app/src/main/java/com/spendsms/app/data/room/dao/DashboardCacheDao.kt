package com.spendsms.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendsms.app.data.room.entity.DashboardCacheEntity

@Dao
interface DashboardCacheDao {

    @Query("SELECT * FROM dashboard_cache WHERE cache_key = :cacheKey LIMIT 1")
    suspend fun findByKey(cacheKey: String): DashboardCacheEntity?

    @Query(
        """
        SELECT * FROM dashboard_cache
        WHERE period_start = :periodStart AND period_end = :periodEnd
        LIMIT 1
        """,
    )
    suspend fun findByPeriod(periodStart: Long, periodEnd: Long): DashboardCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DashboardCacheEntity)

    @Query("DELETE FROM dashboard_cache")
    suspend fun deleteAll()
}
