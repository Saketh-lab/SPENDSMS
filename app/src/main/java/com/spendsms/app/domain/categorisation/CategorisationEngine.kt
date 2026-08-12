package com.spendsms.app.domain.categorisation

import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransferStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assigns one Phase-0 system category (Step-3 Categorisation Engine).
 *
 * Direction/type handling is applied before merchant/keyword rules so refunds,
 * credits, and transfers are not miscategorised as the merchant's spend bucket.
 * User corrections still win.
 */
@Singleton
class CategorisationEngine @Inject constructor() {

    fun categorise(
        merchant: Merchant?,
        direction: TransactionDirection,
        paymentMethod: PaymentMethod,
        transferStatus: TransferStatus,
        userCategoryId: CategoryId? = null,
    ): CategoryId {
        if (userCategoryId != null && isSystemCategory(userCategoryId)) {
            return userCategoryId
        }

        when (direction) {
            TransactionDirection.CREDIT,
            TransactionDirection.REFUND,
            TransactionDirection.REVERSAL,
            -> return SystemCategories.INCOME_AND_REFUNDS
            TransactionDirection.TRANSFER -> return SystemCategories.TRANSFERS
            TransactionDirection.DEBIT -> Unit
        }

        if (transferStatus != TransferStatus.NONE) {
            return SystemCategories.TRANSFERS
        }

        if (paymentMethod == PaymentMethod.ATM) {
            return SystemCategories.CASH_WITHDRAWAL
        }

        val merchantKey = merchant?.key?.value
        if (merchantKey != null) {
            BundledCategoryRules.exactMerchant[merchantKey]?.let { return it }
        }

        val haystack = listOfNotNull(
            merchant?.key?.value,
            merchant?.displayName,
            merchant?.rawNormalized,
        ).joinToString(" ")
        if (haystack.isNotBlank()) {
            for ((pattern, category) in BundledCategoryRules.keywords) {
                if (pattern.containsMatchIn(haystack)) return category
            }
        }

        return SystemCategories.OTHER
    }

    private fun isSystemCategory(id: CategoryId): Boolean =
        SYSTEM_IDS.contains(id)

    companion object {
        private val SYSTEM_IDS: Set<CategoryId> =
            SystemCategories.seed(com.spendsms.app.domain.model.EpochMillis.of(0L))
                .map { it.id }
                .toSet()
    }
}
