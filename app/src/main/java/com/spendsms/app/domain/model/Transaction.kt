package com.spendsms.app.domain.model

import com.spendsms.app.domain.merchant.Merchant

/**
 * Parser output before Room persistence.
 *
 * May carry a one-way [sourceMessageHash] for idempotency, but must never hold
 * raw SMS body text.
 */
data class TransactionCandidate(
    val sourceMessageHash: String,
    val amount: Money,
    val transactionTimestamp: EpochMillis,
    val merchantRaw: String?,
    val institution: String?,
    val maskedAccount: String?,
    val direction: TransactionDirection,
    val paymentMethod: PaymentMethod,
    val referenceHash: String?,
    val confidence: Confidence,
    val parserVersion: ParserVersion,
) {
    init {
        require(sourceMessageHash.isNotBlank()) {
            "TransactionCandidate.sourceMessageHash must not be blank"
        }
        merchantRaw?.let {
            require(it.isNotBlank()) { "merchantRaw, when present, must not be blank" }
        }
        institution?.let {
            require(it.isNotBlank()) { "institution, when present, must not be blank" }
        }
        maskedAccount?.let {
            require(it.isNotBlank()) { "maskedAccount, when present, must not be blank" }
        }
        referenceHash?.let {
            require(it.isNotBlank()) { "referenceHash, when present, must not be blank" }
        }
    }
}

/**
 * Effective local financial transaction — domain shape of Step-3 `transactions`.
 *
 * Raw SMS body is intentionally absent.
 */
data class Transaction(
    val id: TransactionId,
    val sourceMessageHash: String,
    val fingerprint: TransactionFingerprint,
    val timestamp: EpochMillis,
    val amount: Money,
    val merchant: Merchant?,
    val institution: String?,
    val maskedAccount: String?,
    val referenceHash: String?,
    val direction: TransactionDirection,
    val paymentMethod: PaymentMethod,
    val categoryId: CategoryId,
    val confidence: Confidence,
    val parserVersion: ParserVersion,
    val duplicateStatus: DuplicateStatus,
    val possibleDuplicateOf: TransactionId?,
    val transferStatus: TransferStatus,
    val isUserConfirmed: Boolean,
    val createdAt: EpochMillis,
    val updatedAt: EpochMillis,
) {
    init {
        require(sourceMessageHash.isNotBlank()) {
            "Transaction.sourceMessageHash must not be blank"
        }
        institution?.let {
            require(it.isNotBlank()) { "institution, when present, must not be blank" }
        }
        maskedAccount?.let {
            require(it.isNotBlank()) { "maskedAccount, when present, must not be blank" }
        }
        referenceHash?.let {
            require(it.isNotBlank()) { "referenceHash, when present, must not be blank" }
        }
        if (duplicateStatus == DuplicateStatus.NONE) {
            require(possibleDuplicateOf == null) {
                "possibleDuplicateOf must be null when duplicateStatus is NONE"
            }
        }
        require(updatedAt >= createdAt) {
            "updatedAt must be >= createdAt"
        }
    }
}
