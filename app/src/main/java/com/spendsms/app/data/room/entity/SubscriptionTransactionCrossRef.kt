package com.spendsms.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "subscription_transactions",
    primaryKeys = ["subscription_id", "transaction_id"],
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["subscription_id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transaction_id"]),
    ],
)
data class SubscriptionTransactionCrossRef(
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: String,
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
)
