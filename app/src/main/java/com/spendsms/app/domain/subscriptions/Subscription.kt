package com.spendsms.app.domain.subscriptions

import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.SubscriptionFrequency
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.TransactionId

/**
 * Suspected or user-confirmed recurring payment (Step-3 `subscriptions`).
 *
 * Machine-detected results remain [SubscriptionStatus.SUSPECTED] until confirmed.
 */
data class Subscription(
    val id: SubscriptionId,
    val merchantKey: MerchantKey,
    val merchantDisplayName: String?,
    val frequency: SubscriptionFrequency,
    val estimatedAmount: Money?,
    val currency: CurrencyCode,
    val lastPaymentDate: EpochMillis?,
    val estimatedNextDate: EpochMillis?,
    val confidence: Confidence,
    val status: SubscriptionStatus,
    val evidenceTransactionIds: List<TransactionId>,
    val createdAt: EpochMillis,
    val updatedAt: EpochMillis,
) {
    init {
        merchantDisplayName?.let {
            require(it.isNotBlank()) {
                "merchantDisplayName, when present, must not be blank"
            }
        }
        estimatedAmount?.let {
            require(it.currency == currency) {
                "estimatedAmount currency must match subscription currency"
            }
        }
        require(evidenceTransactionIds.size == evidenceTransactionIds.distinct().size) {
            "evidenceTransactionIds must not contain duplicates"
        }
        require(updatedAt >= createdAt) { "updatedAt must be >= createdAt" }
    }
}
