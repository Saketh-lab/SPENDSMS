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

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add ${other.currency.code} to ${currency.code}"
        }
        return Money(amountMinorUnits + other.amountMinorUnits, currency)
    }

    /**
     * Subtracts [other], flooring at zero so dashboard net spending never goes negative.
     */
    fun minusFlooringAtZero(other: Money): Money {
        require(currency == other.currency) {
            "Cannot subtract ${other.currency.code} from ${currency.code}"
        }
        return Money(
            amountMinorUnits = (amountMinorUnits - other.amountMinorUnits).coerceAtLeast(0L),
            currency = currency,
        )
    }

    companion object {
        fun zero(currency: CurrencyCode = CurrencyCode.INR): Money =
            Money(amountMinorUnits = 0L, currency = currency)

        fun ofMinorUnits(amountMinorUnits: Long, currency: CurrencyCode): Money =
            Money(amountMinorUnits = amountMinorUnits, currency = currency)
    }
}
