package com.spendsms.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"],
            childColumns = ["possible_duplicate_of"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["transaction_timestamp"], name = "idx_transactions_timestamp"),
        Index(
            value = ["category_id", "transaction_timestamp"],
            name = "idx_transactions_category_timestamp",
        ),
        Index(
            value = ["merchant_key", "transaction_timestamp"],
            name = "idx_transactions_merchant_timestamp",
        ),
        Index(
            value = ["direction", "transaction_timestamp"],
            name = "idx_transactions_direction_timestamp",
        ),
        Index(
            value = ["transaction_fingerprint"],
            unique = true,
            name = "idx_transactions_fingerprint",
        ),
        Index(value = ["source_message_hash"], name = "idx_transactions_source_hash"),
        Index(
            value = ["amount_minor_units", "currency", "transaction_timestamp"],
            name = "idx_transactions_amount_time",
        ),
        Index(value = ["category_id"]),
        Index(value = ["possible_duplicate_of"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
    @ColumnInfo(name = "source_message_hash")
    val sourceMessageHash: String,
    @ColumnInfo(name = "transaction_fingerprint")
    val transactionFingerprint: String,
    @ColumnInfo(name = "transaction_timestamp")
    val transactionTimestamp: Long,
    @ColumnInfo(name = "amount_minor_units")
    val amountMinorUnits: Long,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "merchant_raw_normalized")
    val merchantRawNormalized: String?,
    @ColumnInfo(name = "merchant_display_name")
    val merchantDisplayName: String?,
    @ColumnInfo(name = "merchant_key")
    val merchantKey: String?,
    @ColumnInfo(name = "institution")
    val institution: String?,
    @ColumnInfo(name = "masked_account")
    val maskedAccount: String?,
    @ColumnInfo(name = "reference_hash")
    val referenceHash: String?,
    @ColumnInfo(name = "direction")
    val direction: String,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "confidence")
    val confidence: Double,
    @ColumnInfo(name = "parser_version")
    val parserVersion: String,
    @ColumnInfo(name = "duplicate_status")
    val duplicateStatus: String,
    @ColumnInfo(name = "possible_duplicate_of")
    val possibleDuplicateOf: String?,
    @ColumnInfo(name = "transfer_status")
    val transferStatus: String,
    @ColumnInfo(name = "is_user_confirmed")
    val isUserConfirmed: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
