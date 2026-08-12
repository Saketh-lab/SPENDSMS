package com.spendsms.app.domain.semantics

import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.TransactionCandidate
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransferStatus

/**
 * Post-parse classified candidate: merchant, category, and spending semantics.
 *
 * Preserves the original [candidate] (parser confidence, version, extracted
 * fields). Does not assign a fingerprint or persist a [com.spendsms.app.domain.model.Transaction].
 */
data class ClassifiedTransaction(
    val candidate: TransactionCandidate,
    val merchant: Merchant?,
    val categoryId: CategoryId,
    val direction: TransactionDirection,
    val transferStatus: TransferStatus,
    val spending: SpendingInclusion,
) {
    val confidence get() = candidate.confidence
    val parserVersion get() = candidate.parserVersion
    val amount get() = candidate.amount
}
