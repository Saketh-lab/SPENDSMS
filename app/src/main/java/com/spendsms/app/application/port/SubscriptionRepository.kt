package com.spendsms.app.application.port

import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.subscriptions.Subscription

/**
 * Suspected/confirmed subscription store (Step-3 `subscriptions` + evidence links).
 */
interface SubscriptionRepository {

    suspend fun findById(id: SubscriptionId): Subscription?

    suspend fun findByMerchantKey(merchantKey: MerchantKey): Subscription?

    suspend fun findByStatus(status: SubscriptionStatus): List<Subscription>

    suspend fun listAll(): List<Subscription>

    suspend fun upsert(subscription: Subscription): Subscription

    suspend fun updateStatus(id: SubscriptionId, status: SubscriptionStatus): Subscription?

    suspend fun deleteAll()
}
