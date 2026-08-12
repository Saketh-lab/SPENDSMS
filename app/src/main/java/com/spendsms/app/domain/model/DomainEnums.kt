package com.spendsms.app.domain.model

/**
 * Direction / transaction type extracted from financial SMS.
 *
 * Closed set covering the debit/credit/refund/transfer/reversal cases called out
 * in Steps 1–3. Exact classification rules live in later domain services.
 */
enum class TransactionDirection {
    DEBIT,
    CREDIT,
    REFUND,
    REVERSAL,
    TRANSFER,
}

/**
 * Payment rail / method when identifiable.
 *
 * Step-3 stores this as TEXT enum without listing values. This Phase-0 set
 * mirrors the PRD's supported Indian alert types without inventing Phase-1 rails.
 */
enum class PaymentMethod {
    UPI,
    CARD,
    ATM,
    NET_BANKING,
    WALLET,
    BILL_PAYMENT,
    UNKNOWN,
}

/**
 * Duplicate classification persisted with a transaction (Step-3).
 */
enum class DuplicateStatus {
    NONE,
    SUSPECTED,
    CONFIRMED,
}

/**
 * Own-account / transfer classification persisted with a transaction (Step-3).
 */
enum class TransferStatus {
    NONE,
    SUSPECTED,
    CONFIRMED,
}

/**
 * Resumable scan lifecycle (Step-3 `scan_state.status`).
 */
enum class ScanStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    INTERRUPTED,
    CANCELLED,
    FAILED,
}

/**
 * Parser-rule package lifecycle (Step-3 `parser_metadata.status`).
 */
enum class ParserBundleStatus {
    INSTALLED,
    ACTIVE,
    ROLLBACK,
    INVALID,
}

/**
 * Suspected-subscription lifecycle (Step-3).
 */
enum class SubscriptionStatus {
    SUSPECTED,
    CONFIRMED,
    DISMISSED,
    POSSIBLY_INACTIVE,
}

/**
 * Recurring-frequency estimate. Stored as TEXT in Step-3; values are not exhaustively
 * listed, so this Phase-0 set stays coarse.
 */
enum class SubscriptionFrequency {
    WEEKLY,
    MONTHLY,
    YEARLY,
    UNKNOWN,
}

/**
 * Correctable fields supported by the Phase-0 correction service (Step-3 §3.10).
 */
enum class CorrectionField {
    MERCHANT,
    CATEGORY,
    DIRECTION,
    DUPLICATE_STATUS,
    TRANSFER_STATUS,
    SUBSCRIPTION_STATUS,
    NOT_A_TRANSACTION,
}
