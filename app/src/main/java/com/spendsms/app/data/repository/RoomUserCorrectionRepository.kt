package com.spendsms.app.data.repository

import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.data.room.dao.UserCorrectionDao
import com.spendsms.app.data.room.mapper.toDomain
import com.spendsms.app.data.room.mapper.toEntity
import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CorrectionId
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.TransactionId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomUserCorrectionRepository @Inject constructor(
    private val userCorrectionDao: UserCorrectionDao,
) : UserCorrectionRepository {

    override suspend fun findById(id: CorrectionId): UserCorrection? =
        userCorrectionDao.findById(id.value)?.toDomain()

    override suspend fun findByTransactionId(transactionId: TransactionId): List<UserCorrection> =
        userCorrectionDao.findByTransactionId(transactionId.value).map { it.toDomain() }

    override suspend fun findFutureRules(
        merchantMatchKey: MerchantKey,
        field: CorrectionField,
    ): List<UserCorrection> =
        userCorrectionDao.findFutureRules(
            merchantMatchKey = merchantMatchKey.value,
            fieldName = field.name,
        ).map { it.toDomain() }

    override suspend fun save(correction: UserCorrection): UserCorrection {
        userCorrectionDao.upsert(correction.toEntity())
        return correction
    }

    override suspend fun deleteByTransactionId(transactionId: TransactionId) {
        userCorrectionDao.deleteByTransactionId(transactionId.value)
    }

    override suspend fun deleteAll() {
        userCorrectionDao.deleteAll()
    }
}
