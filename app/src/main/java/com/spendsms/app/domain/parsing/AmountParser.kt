package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.Money

/**
 * Parses Indian-style currency amounts from declarative capture groups into minor units.
 */
object AmountParser {

    fun parseMoney(rawAmount: String, rawCurrency: String?): Money? {
        val currency = parseCurrency(rawCurrency) ?: CurrencyCode.INR
        val minor = parseMinorUnits(rawAmount) ?: return null
        return Money.ofMinorUnits(minor, currency)
    }

    fun parseCurrency(raw: String?): CurrencyCode? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().uppercase()
            .replace("₹", "INR")
            .replace(".", "")
        return when {
            normalized == "INR" ||
                normalized == "RS" ||
                normalized == "RUPEE" ||
                normalized == "RUPEES" ||
                normalized.startsWith("INR") -> CurrencyCode.INR
            else -> try {
                CurrencyCode.of(normalized)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Accepts `1,234.56`, `1234.5`, `1234` → paise/minor units (×100).
     */
    fun parseMinorUnits(raw: String): Long? {
        val cleaned = raw.trim()
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        val match = AMOUNT_PATTERN.matchEntire(cleaned) ?: return null
        val whole = match.groupValues[1].toLongOrNull() ?: return null
        val fraction = match.groupValues[2]
        val fracNormalized = when {
            fraction.isEmpty() -> 0
            fraction.length == 1 -> fraction.toInt() * 10
            else -> fraction.take(2).toInt()
        }
        return whole * 100L + fracNormalized
    }

    private val AMOUNT_PATTERN = Regex("""^(\d+)(?:\.(\d{1,2}))?$""")
}
