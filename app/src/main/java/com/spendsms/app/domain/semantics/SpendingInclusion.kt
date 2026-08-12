package com.spendsms.app.domain.semantics

/**
 * How a classified transaction contributes to dashboard spending totals.
 *
 * Dashboard math (Prompt 12) sums these flags; this layer only decides them.
 */
enum class SpendingRole {
    /** Ordinary debit/cash-out: adds to gross and net spending. */
    GROSS_EXPENSE,

    /** Refund or reversal: counted in refunds and reduces net spending. */
    REFUND,

    /** Inbound credit/income: counted in credits, not spending. */
    CREDIT,

    /** Own-account or explicit transfer: excluded from spending totals. */
    TRANSFER,

    /** User-excluded or not-a-transaction: omitted from all money totals. */
    EXCLUDED,
}

data class SpendingInclusion(
    val role: SpendingRole,
    val includeInGrossSpending: Boolean,
    val includeInNetSpending: Boolean,
    val includeInCredits: Boolean,
    val includeInRefunds: Boolean,
    val excludedFromSpendingTotals: Boolean,
) {
    companion object {
        fun of(role: SpendingRole): SpendingInclusion = when (role) {
            SpendingRole.GROSS_EXPENSE -> SpendingInclusion(
                role = role,
                includeInGrossSpending = true,
                includeInNetSpending = true,
                includeInCredits = false,
                includeInRefunds = false,
                excludedFromSpendingTotals = false,
            )
            SpendingRole.REFUND -> SpendingInclusion(
                role = role,
                includeInGrossSpending = false,
                includeInNetSpending = false,
                includeInCredits = false,
                includeInRefunds = true,
                excludedFromSpendingTotals = false,
            )
            SpendingRole.CREDIT -> SpendingInclusion(
                role = role,
                includeInGrossSpending = false,
                includeInNetSpending = false,
                includeInCredits = true,
                includeInRefunds = false,
                excludedFromSpendingTotals = false,
            )
            SpendingRole.TRANSFER -> SpendingInclusion(
                role = role,
                includeInGrossSpending = false,
                includeInNetSpending = false,
                includeInCredits = false,
                includeInRefunds = false,
                excludedFromSpendingTotals = true,
            )
            SpendingRole.EXCLUDED -> SpendingInclusion(
                role = role,
                includeInGrossSpending = false,
                includeInNetSpending = false,
                includeInCredits = false,
                includeInRefunds = false,
                excludedFromSpendingTotals = true,
            )
        }
    }
}
