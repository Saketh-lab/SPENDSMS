package com.spendsms.app.domain.model

/**
 * Parser confidence in the inclusive range `[0.0, 1.0]`.
 */
@JvmInline
value class Confidence private constructor(val value: Double) {
    init {
        require(value in MIN..MAX) {
            "Confidence must be in [$MIN, $MAX], was $value"
        }
        require(!value.isNaN()) { "Confidence must not be NaN" }
    }

    companion object {
        const val MIN: Double = 0.0
        const val MAX: Double = 1.0

        fun of(value: Double): Confidence = Confidence(value)
    }
}
