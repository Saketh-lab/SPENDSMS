package com.spendsms.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_state",
    indices = [
        Index(value = ["status", "updated_at"], name = "idx_scan_state_status_updated"),
    ],
)
data class ScanStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "scan_id")
    val scanId: String,
    @ColumnInfo(name = "start_date")
    val startDate: Long,
    @ColumnInfo(name = "end_date")
    val endDate: Long,
    @ColumnInfo(name = "last_processed_message_id")
    val lastProcessedMessageId: String?,
    @ColumnInfo(name = "parser_version")
    val parserVersion: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "processed_count")
    val processedCount: Int,
    @ColumnInfo(name = "accepted_count")
    val acceptedCount: Int,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
