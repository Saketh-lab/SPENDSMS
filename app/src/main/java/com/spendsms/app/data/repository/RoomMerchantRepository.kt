package com.spendsms.app.data.repository

import com.spendsms.app.application.port.MerchantRepository
import com.spendsms.app.data.room.dao.TransactionDao
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.merchant.MerchantNormalizer
import com.spendsms.app.domain.model.MerchantKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-0 merchant lookup without an independent merchant table (Step-3).
 * Keys come from [MerchantNormalizer]; prior transactions may supply display names.
 */
@Singleton
class RoomMerchantRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val merchantNormalizer: MerchantNormalizer,
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
        val normalized = requireNotNull(
            merchantNormalizer.normalize(rawNormalized, institution),
        ) { "rawNormalized must not be blank" }
        return findByKey(normalized.key) ?: normalized
    }
}
