package com.spendsms.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    indices = [
        Index(value = ["status"], name = "idx_subscriptions_status"),
        Index(value = ["merchant_key"], name = "idx_subscriptions_merchant"),
    ],
)
data class SubscriptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: String,
    @ColumnInfo(name = "merchant_key")
    val merchantKey: String,
    @ColumnInfo(name = "merchant_display_name")
    val merchantDisplayName: String?,
    @ColumnInfo(name = "frequency")
    val frequency: String,
    @ColumnInfo(name = "estimated_amount_minor")
    val estimatedAmountMinor: Long?,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "last_payment_date")
    val lastPaymentDate: Long?,
    @ColumnInfo(name = "estimated_next_date")
    val estimatedNextDate: Long?,
    @ColumnInfo(name = "confidence")
    val confidence: Double,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
