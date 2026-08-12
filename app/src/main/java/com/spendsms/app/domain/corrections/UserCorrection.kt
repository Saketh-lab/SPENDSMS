package com.spendsms.app.domain.corrections

import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CorrectionId
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.TransactionId

/**
 * User decision stored separately from parser output (Step-3 `user_corrections`).
 *
 * Corrections are authoritative over parser-derived values on rescans.
 */
data class UserCorrection(
    val id: CorrectionId,
    val transactionId: TransactionId,
    val field: CorrectionField,
    val oldValue: String?,
    val newValue: String,
    val applyToFuture: Boolean,
    val merchantMatchKey: MerchantKey?,
    val createdAt: EpochMillis,
    val updatedAt: EpochMillis,
) {
    init {
        require(newValue.isNotBlank()) { "UserCorrection.newValue must not be blank" }
        oldValue?.let {
            require(it.isNotBlank()) { "oldValue, when present, must not be blank" }
        }
        if (applyToFuture) {
            require(merchantMatchKey != null) {
                "merchantMatchKey is required when applyToFuture is true"
            }
        }
        require(updatedAt >= createdAt) { "updatedAt must be >= createdAt" }
    }
}
