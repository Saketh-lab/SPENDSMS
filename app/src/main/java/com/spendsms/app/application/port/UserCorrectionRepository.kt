package com.spendsms.app.application.port

import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CorrectionId
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.TransactionId

/**
 * Persistent user corrections that outrank parser output on rescans (Step-3).
 */
interface UserCorrectionRepository {

    suspend fun findById(id: CorrectionId): UserCorrection?

    suspend fun findByTransactionId(transactionId: TransactionId): List<UserCorrection>

    suspend fun findFutureRules(
        merchantMatchKey: MerchantKey,
        field: CorrectionField,
    ): List<UserCorrection>

    /** One-shot load of future-apply rules for a merchant (all fields). */
    suspend fun findFutureRulesForMerchant(merchantMatchKey: MerchantKey): List<UserCorrection>

    /** Bulk lookup used by dashboard/subscription recompute — avoids per-row correction queries. */
    suspend fun findTransactionIdsWithField(field: CorrectionField): Set<TransactionId>

    suspend fun save(correction: UserCorrection): UserCorrection

    suspend fun deleteByTransactionId(transactionId: TransactionId)

    suspend fun deleteAll()
}
