package com.spendsms.app.application.analysis

import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.domain.categorisation.CategorisationEngine
import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.merchant.MerchantNormalizer
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.TransactionCandidate
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.ClassifiedTransaction
import com.spendsms.app.domain.semantics.TransactionSemantics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies merchant normalization, categorisation, and spending semantics to a
 * Prompt-7 [TransactionCandidate]. Loads future-apply user corrections when a
 * merchant key is available.
 */
@Singleton
class TransactionClassificationService @Inject constructor(
    private val merchantNormalizer: MerchantNormalizer,
    private val categorisationEngine: CategorisationEngine,
    private val semantics: TransactionSemantics,
    private val userCorrectionRepository: UserCorrectionRepository,
) {

    suspend fun classify(candidate: TransactionCandidate): ClassifiedTransaction {
        val normalized = merchantNormalizer.normalize(
            raw = candidate.merchantRaw,
            institution = candidate.institution,
        )
        val future = loadFutureCorrections(normalized)
        return classifyWithCorrections(candidate, future)
    }

    /**
     * Deterministic path for tests: pass already-loaded future-apply corrections.
     */
    fun classifyWithCorrections(
        candidate: TransactionCandidate,
        futureCorrections: List<UserCorrection> = emptyList(),
    ): ClassifiedTransaction {
        val normalizedMerchant = merchantNormalizer.normalize(
            raw = candidate.merchantRaw,
            institution = candidate.institution,
        )
        val merchantOverride = latest(futureCorrections, CorrectionField.MERCHANT)?.newValue
        val merchant = when {
            normalizedMerchant == null && merchantOverride == null -> null
            normalizedMerchant == null -> Merchant(
                key = MerchantKey.of(MerchantNormalizer.UNKNOWN_KEY),
                displayName = merchantOverride!!,
                rawNormalized = candidate.merchantRaw,
            )
            merchantOverride != null -> normalizedMerchant.copy(displayName = merchantOverride)
            else -> normalizedMerchant
        }

        val excludedByUser = latest(futureCorrections, CorrectionField.NOT_A_TRANSACTION) != null
        val userDirection = latest(futureCorrections, CorrectionField.DIRECTION)
            ?.newValue
            ?.let { parseDirection(it) }
        val userTransfer = latest(futureCorrections, CorrectionField.TRANSFER_STATUS)
            ?.newValue
            ?.let { parseTransferStatus(it) }
        val userCategory = latest(futureCorrections, CorrectionField.CATEGORY)
            ?.newValue
            ?.let { runCatching { CategoryId.of(it) }.getOrNull() }

        val decision = semantics.resolve(
            parsedDirection = candidate.direction,
            merchant = merchant,
            userDirection = userDirection,
            userTransferStatus = userTransfer,
            excludedByUser = excludedByUser,
        )
        val categoryId = categorisationEngine.categorise(
            merchant = merchant,
            direction = decision.direction,
            paymentMethod = candidate.paymentMethod,
            transferStatus = decision.transferStatus,
            userCategoryId = userCategory,
        )
        return ClassifiedTransaction(
            candidate = candidate,
            merchant = merchant,
            categoryId = categoryId,
            direction = decision.direction,
            transferStatus = decision.transferStatus,
            spending = decision.spending,
        )
    }

    private suspend fun loadFutureCorrections(merchant: Merchant?): List<UserCorrection> {
        if (merchant == null) return emptyList()
        return FUTURE_FIELDS.flatMap { field ->
            userCorrectionRepository.findFutureRules(merchant.key, field)
        }
    }

    private fun latest(
        corrections: List<UserCorrection>,
        field: CorrectionField,
    ): UserCorrection? =
        corrections
            .filter { it.field == field && it.applyToFuture }
            .maxByOrNull { it.updatedAt.toEpochMillis }

    private fun parseDirection(raw: String): TransactionDirection? =
        runCatching { TransactionDirection.valueOf(raw.trim().uppercase()) }.getOrNull()

    private fun parseTransferStatus(raw: String): TransferStatus? =
        runCatching { TransferStatus.valueOf(raw.trim().uppercase()) }.getOrNull()

    companion object {
        private val FUTURE_FIELDS = listOf(
            CorrectionField.MERCHANT,
            CorrectionField.CATEGORY,
            CorrectionField.DIRECTION,
            CorrectionField.TRANSFER_STATUS,
            CorrectionField.NOT_A_TRANSACTION,
        )
    }
}
