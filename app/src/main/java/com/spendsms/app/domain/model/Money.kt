package com.spendsms.app.domain.model

/**
 * Monetary amount stored in integer minor units (paise for INR).
 *
 * Matches Step-3: never use floating-point for financial amounts.
 */
data class Money(
    val amountMinorUnits: Long,
    val currency: CurrencyCode,
) {
    init {
        require(amountMinorUnits >= 0L) {
            "Money amountMinorUnits must be >= 0, was $amountMinorUnits"
        }
    }

    companion object {
        fun zero(currency: CurrencyCode = CurrencyCode.INR): Money =
            Money(amountMinorUnits = 0L, currency = currency)

        fun ofMinorUnits(amountMinorUnits: Long, currency: CurrencyCode): Money =
            Money(amountMinorUnits = amountMinorUnits, currency = currency)
    }
}
