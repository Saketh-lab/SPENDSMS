package com.spendsms.app.data.repository

import com.spendsms.app.application.port.MerchantRepository
import com.spendsms.app.data.room.dao.TransactionDao
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.MerchantKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-0 merchant lookup without an independent merchant table (Step-3).
 * Resolves from prior transactions when available; otherwise derives a local key.
 */
@Singleton
class RoomMerchantRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) : MerchantRepository {

    override suspend fun findByKey(key: MerchantKey): Merchant? {
        val row = transactionDao.findByMerchantKey(
            merchantKey = key.value,
            startInclusive = null,
            endInclusive = null,
        ).firstOrNull() ?: return null
        return row.toDomain().merchant
    }

    override suspend fun resolve(rawNormalized: String, institution: String?): Merchant {
        require(rawNormalized.isNotBlank()) { "rawNormalized must not be blank" }
        val cleaned = rawNormalized.trim().replace(Regex("\\s+"), " ")
        val key = MerchantKey.of(buildKey(cleaned, institution))
        return findByKey(key) ?: Merchant(
            key = key,
            displayName = cleaned,
            rawNormalized = cleaned,
        )
    }

    private fun buildKey(cleaned: String, institution: String?): String {
        val base = cleaned.lowercase()
        val inst = institution?.trim()?.lowercase().orEmpty()
        return if (inst.isEmpty()) base else "$inst|$base"
    }
}
