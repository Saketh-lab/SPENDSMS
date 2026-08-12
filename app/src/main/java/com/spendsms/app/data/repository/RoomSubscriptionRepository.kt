package com.spendsms.app.data.repository

import com.spendsms.app.application.port.SubscriptionRepository
import com.spendsms.app.data.room.dao.SubscriptionDao
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.data.room.mapper.toEntity
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.subscriptions.Subscription
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
) : SubscriptionRepository {

    override suspend fun findById(id: SubscriptionId): Subscription? {
        val entity = subscriptionDao.findById(id.value) ?: return null
        val evidence = subscriptionDao.getEvidenceTransactionIds(id.value)
        return entity.toDomain(evidence)
    }

    override suspend fun findByMerchantKey(merchantKey: MerchantKey): Subscription? {
        val entity = subscriptionDao.findByMerchantKey(merchantKey.value) ?: return null
        val evidence = subscriptionDao.getEvidenceTransactionIds(entity.subscriptionId)
        return entity.toDomain(evidence)
    }

    override suspend fun findByStatus(status: SubscriptionStatus): List<Subscription> =
        subscriptionDao.findByStatus(status.name).map { entity ->
            entity.toDomain(subscriptionDao.getEvidenceTransactionIds(entity.subscriptionId))
        }

    override suspend fun listAll(): List<Subscription> =
        subscriptionDao.listAll().map { entity ->
            entity.toDomain(subscriptionDao.getEvidenceTransactionIds(entity.subscriptionId))
        }

    override suspend fun upsert(subscription: Subscription): Subscription {
        subscriptionDao.upsertWithEvidence(
            entity = subscription.toEntity(),
            evidenceTransactionIds = subscription.evidenceTransactionIds.map { it.value },
        )
        return subscription
    }

    override suspend fun updateStatus(
        id: SubscriptionId,
        status: SubscriptionStatus,
    ): Subscription? {
        val existing = findById(id) ?: return null
        val updated = existing.copy(
            status = status,
            updatedAt = EpochMillis.of(System.currentTimeMillis()),
        )
        return upsert(updated)
    }

    override suspend fun deleteAll() {
        subscriptionDao.deleteAll()
    }
}
