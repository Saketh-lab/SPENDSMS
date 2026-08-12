package com.spendsms.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Optional derived dashboard cache (Step-3 §5.8).
 * Payload is opaque JSON owned by later cache adapters — not raw SMS.
 */
@Entity(tableName = "dashboard_cache")
data class DashboardCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    @ColumnInfo(name = "period_start")
    val periodStart: Long,
    @ColumnInfo(name = "period_end")
    val periodEnd: Long,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "source_revision")
    val sourceRevision: Long,
    @ColumnInfo(name = "calculated_at")
    val calculatedAt: Long,
)
