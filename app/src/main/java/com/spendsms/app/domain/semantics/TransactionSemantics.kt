package com.spendsms.app.domain.semantics

import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransferStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direction, transfer, refund/reversal, and spending-inclusion rules (FR-08).
 *
 * Own-account transfers are marked [TransferStatus.SUSPECTED] rather than
 * silently confirmed. Parser [TransactionCandidate] direction is preserved
 * unless a user correction overrides it.
 */
@Singleton
class TransactionSemantics @Inject constructor() {

    fun resolve(
        parsedDirection: TransactionDirection,
        merchant: Merchant?,
        userDirection: TransactionDirection? = null,
        userTransferStatus: TransferStatus? = null,
        excludedByUser: Boolean = false,
    ): SemanticDecision {
        if (excludedByUser) {
            val direction = userDirection ?: parsedDirection
            return SemanticDecision(
                direction = direction,
                transferStatus = userTransferStatus ?: TransferStatus.NONE,
                spending = SpendingInclusion.of(SpendingRole.EXCLUDED),
            )
        }

        val direction = userDirection ?: parsedDirection
        val inferredTransfer = inferTransferStatus(direction, merchant)
        val transferStatus = userTransferStatus ?: inferredTransfer

        val role = spendingRole(direction, transferStatus)
        return SemanticDecision(
            direction = direction,
            transferStatus = transferStatus,
            spending = SpendingInclusion.of(role),
        )
    }

    private fun inferTransferStatus(
        direction: TransactionDirection,
        merchant: Merchant?,
    ): TransferStatus {
        if (direction == TransactionDirection.TRANSFER) {
            return TransferStatus.SUSPECTED
        }
        if (looksLikeOwnAccount(merchant)) {
            return TransferStatus.SUSPECTED
        }
        return TransferStatus.NONE
    }

    private fun spendingRole(
        direction: TransactionDirection,
        transferStatus: TransferStatus,
    ): SpendingRole {
        if (transferStatus != TransferStatus.NONE || direction == TransactionDirection.TRANSFER) {
            return SpendingRole.TRANSFER
        }
        return when (direction) {
            TransactionDirection.DEBIT -> SpendingRole.GROSS_EXPENSE
            TransactionDirection.CREDIT -> SpendingRole.CREDIT
            TransactionDirection.REFUND,
            TransactionDirection.REVERSAL,
            -> SpendingRole.REFUND
            TransactionDirection.TRANSFER -> SpendingRole.TRANSFER
        }
    }

    private fun looksLikeOwnAccount(merchant: Merchant?): Boolean {
        if (merchant == null) return false
        val haystack = listOfNotNull(
            merchant.displayName,
            merchant.rawNormalized,
            merchant.key.value,
        ).joinToString(" ")
        return OWN_ACCOUNT.containsMatchIn(haystack)
    }

    data class SemanticDecision(
        val direction: TransactionDirection,
        val transferStatus: TransferStatus,
        val spending: SpendingInclusion,
    )

    companion object {
        private val OWN_ACCOUNT = Regex(
            """(?i)\b(own\s*a/?c|own\s+account|self\s+transfer|to\s+self|my\s+account)\b""",
        )
    }
}
