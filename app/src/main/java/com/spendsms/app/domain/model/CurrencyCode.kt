package com.spendsms.app.domain.model

/**
 * ISO-style currency code (e.g. INR).
 *
 * String-backed rather than a closed enum so parser/rule packages can introduce
 * additional codes without an app release, while still rejecting blank/malformed values.
 */
@JvmInline
value class CurrencyCode private constructor(val code: String) {
    init {
        require(CODE_PATTERN.matches(code)) {
            "Currency code must be a 3-letter ISO-style uppercase code, was '$code'"
        }
    }

    override fun toString(): String = code

    companion object {
        private val CODE_PATTERN = Regex("^[A-Z]{3}$")

        val INR: CurrencyCode = CurrencyCode("INR")

        fun of(raw: String): CurrencyCode {
            val normalized = raw.trim().uppercase()
            return CurrencyCode(normalized)
        }
    }
}
