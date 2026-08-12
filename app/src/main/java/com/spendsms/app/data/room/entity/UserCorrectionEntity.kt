package com.spendsms.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_corrections",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transaction_id"], name = "idx_corrections_transaction"),
        // Partial WHERE apply_to_future = 1 is created in SpendSmsDatabaseCallback.
        Index(
            value = ["merchant_match_key", "field_name"],
            name = "idx_corrections_future_rule_columns",
        ),
    ],
)
data class UserCorrectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "correction_id")
    val correctionId: String,
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
    @ColumnInfo(name = "field_name")
    val fieldName: String,
    @ColumnInfo(name = "old_value")
    val oldValue: String?,
    @ColumnInfo(name = "new_value")
    val newValue: String,
    @ColumnInfo(name = "apply_to_future")
    val applyToFuture: Boolean,
    @ColumnInfo(name = "merchant_match_key")
    val merchantMatchKey: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
