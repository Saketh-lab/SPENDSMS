package com.spendsms.app.application.corrections

import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.SubscriptionRepository
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.application.subscriptions.SubscriptionDetectionService
import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.merchant.MerchantNormalizer
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CorrectionId
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.TransactionSemantics
import com.spendsms.app.domain.subscriptions.Subscription
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun interface CorrectionIdFactory {
    fun next(): CorrectionId
}

@Singleton
class UuidCorrectionIdFactory @Inject constructor() : CorrectionIdFactory {
    override fun next(): CorrectionId = CorrectionId.of(UUID.randomUUID().toString())
}

data class ApplyCorrectionRequest(
    val transactionId: TransactionId,
    val field: CorrectionField,
    val newValue: String,
    val applyToFuture: Boolean = false,
    val merchantMatchKey: MerchantKey? = null,
    /** Optional analysis period whose dashboard should be recomputed after apply. */
    val dashboardPeriod: AnalysisPeriod? = null,
)

sealed class ApplyCorrectionResult {
    data class Applied(
        val correction: UserCorrection,
        val transaction: Transaction?,
        val subscription: Subscription? = null,
    ) : ApplyCorrectionResult()

    data class Failed(
        val reason: CorrectionFailureReason,
        val detail: String,
    ) : ApplyCorrectionResult()
}

enum class CorrectionFailureReason {
    TRANSACTION_NOT_FOUND,
    INVALID_VALUE,
    INVALID_FUTURE_RULE,
}

/**
 * Persists user corrections separately from parser output and materialises
 * effective transaction/subscription state (Step-3 User Correction Service).
 */
@Singleton
class UserCorrectionService @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userCorrectionRepository: UserCorrectionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val merchantNormalizer: MerchantNormalizer,
    private val semantics: TransactionSemantics,
    private val dashboardService: DashboardService,
    private val subscriptionDetectionService: SubscriptionDetectionService,
    private val correctionIdFactory: CorrectionIdFactory,
) {

    suspend fun apply(
        request: ApplyCorrectionRequest,
        now: EpochMillis,
    ): ApplyCorrectionResult {
        val transaction = transactionRepository.findById(request.transactionId)
            ?: return ApplyCorrectionResult.Failed(
                CorrectionFailureReason.TRANSACTION_NOT_FOUND,
                "Transaction ${request.transactionId.value} not found",
            )
        if (request.applyToFuture && request.merchantMatchKey == null &&
            transaction.merchant == null && request.field != CorrectionField.SUBSCRIPTION_STATUS
        ) {
            return ApplyCorrectionResult.Failed(
                CorrectionFailureReason.INVALID_FUTURE_RULE,
                "merchantMatchKey is required when applyToFuture is true",
            )
        }

        val oldValue = currentValue(transaction, request.field)
        val merchantMatchKey = request.merchantMatchKey
            ?: transaction.merchant?.key.takeIf { request.applyToFuture }

        val correction = try {
            UserCorrection(
                id = correctionIdFactory.next(),
                transactionId = transaction.id,
                field = request.field,
                oldValue = oldValue,
                newValue = request.newValue.trim(),
                applyToFuture = request.applyToFuture,
                merchantMatchKey = merchantMatchKey,
                createdAt = now,
                updatedAt = now,
            )
        } catch (e: IllegalArgumentException) {
            return ApplyCorrectionResult.Failed(
                CorrectionFailureReason.INVALID_VALUE,
                e.message ?: "Invalid correction",
            )
        }

        val materialised = try {
            materialise(transaction, request.field, request.newValue.trim(), now)
        } catch (e: IllegalArgumentException) {
            return ApplyCorrectionResult.Failed(
                CorrectionFailureReason.INVALID_VALUE,
                e.message ?: "Invalid correction value",
            )
        }

        userCorrectionRepository.save(correction)
        val updatedTx = materialised.transaction?.let { transactionRepository.update(it) }
        val updatedSub = materialised.subscription?.let { subscriptionRepository.upsert(it) }

        dashboardService.invalidateCache()
        if (shouldRefreshSubscriptions(request.field)) {
            subscriptionDetectionService.detectAndPersist(
                period = request.dashboardPeriod,
                now = now,
            )
        }
        request.dashboardPeriod?.let { period ->
            dashboardService.recomputeAndCache(period)
        }

        return ApplyCorrectionResult.Applied(
            correction = correction,
            transaction = updatedTx ?: transaction,
            subscription = updatedSub,
        )
    }

    private fun currentValue(transaction: Transaction, field: CorrectionField): String? =
        when (field) {
            CorrectionField.MERCHANT -> transaction.merchant?.displayName
            CorrectionField.CATEGORY -> transaction.categoryId.value
            CorrectionField.DIRECTION -> transaction.direction.name
            CorrectionField.DUPLICATE_STATUS -> transaction.duplicateStatus.name
            CorrectionField.TRANSFER_STATUS -> transaction.transferStatus.name
            CorrectionField.SUBSCRIPTION_STATUS -> null
            CorrectionField.NOT_A_TRANSACTION -> null
        }

    private suspend fun materialise(
        transaction: Transaction,
        field: CorrectionField,
        newValue: String,
        now: EpochMillis,
    ): Materialised {
        return when (field) {
            CorrectionField.MERCHANT -> {
                val normalized = merchantNormalizer.normalize(newValue)
                    ?: Merchant(
                        key = MerchantKey.of(MerchantNormalizer.UNKNOWN_KEY),
                        displayName = newValue,
                        rawNormalized = newValue,
                    )
                Materialised(
                    transaction = transaction.copy(
                        merchant = normalized.copy(displayName = newValue),
                        isUserConfirmed = true,
                        updatedAt = now,
                    ),
                )
            }
            CorrectionField.CATEGORY -> Materialised(
                transaction = transaction.copy(
                    categoryId = CategoryId.of(newValue),
                    isUserConfirmed = true,
                    updatedAt = now,
                ),
            )
            CorrectionField.DIRECTION -> {
                val direction = TransactionDirection.valueOf(newValue.uppercase())
                val decision = semantics.resolve(
                    parsedDirection = direction,
                    merchant = transaction.merchant,
                    userDirection = direction,
                    userTransferStatus = transaction.transferStatus,
                )
                Materialised(
                    transaction = transaction.copy(
                        direction = decision.direction,
                        transferStatus = decision.transferStatus,
                        isUserConfirmed = true,
                        updatedAt = now,
                    ),
                )
            }
            CorrectionField.DUPLICATE_STATUS -> Materialised(
                transaction = transaction.copy(
                    duplicateStatus = DuplicateStatus.valueOf(newValue.uppercase()),
                    possibleDuplicateOf = if (
                        DuplicateStatus.valueOf(newValue.uppercase()) == DuplicateStatus.NONE
                    ) {
                        null
                    } else {
                        transaction.possibleDuplicateOf
                    },
                    isUserConfirmed = true,
                    updatedAt = now,
                ),
            )
            CorrectionField.TRANSFER_STATUS -> {
                val transfer = TransferStatus.valueOf(newValue.uppercase())
                Materialised(
                    transaction = transaction.copy(
                        transferStatus = transfer,
                        isUserConfirmed = true,
                        updatedAt = now,
                    ),
                )
            }
            CorrectionField.NOT_A_TRANSACTION -> Materialised(
                transaction = transaction.copy(
                    isUserConfirmed = true,
                    updatedAt = now,
                ),
            )
            CorrectionField.SUBSCRIPTION_STATUS -> {
                val status = SubscriptionStatus.valueOf(newValue.uppercase())
                val merchantKey = transaction.merchant?.key
                    ?: throw IllegalArgumentException("Subscription correction requires a merchant")
                val existing = subscriptionRepository.findByMerchantKey(merchantKey)
                    ?: throw IllegalArgumentException("No subscription for merchant ${merchantKey.value}")
                Materialised(
                    subscription = existing.copy(status = status, updatedAt = now),
                )
            }
        }
    }

    private fun shouldRefreshSubscriptions(field: CorrectionField): Boolean =
        field == CorrectionField.MERCHANT ||
            field == CorrectionField.DIRECTION ||
            field == CorrectionField.TRANSFER_STATUS ||
            field == CorrectionField.NOT_A_TRANSACTION ||
            field == CorrectionField.DUPLICATE_STATUS

    private data class Materialised(
        val transaction: Transaction? = null,
        val subscription: Subscription? = null,
    )
}
