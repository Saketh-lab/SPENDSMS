package com.spendsms.app.domain.model

/**
 * UTC instant as epoch milliseconds.
 *
 * Matches Step-3 persistence convention without depending on Android or java.time
 * clocks in the domain model itself.
 */
@JvmInline
value class EpochMillis private constructor(val toEpochMillis: Long) {
    init {
        require(toEpochMillis >= 0L) {
            "EpochMillis must be >= 0, was $toEpochMillis"
        }
    }

    operator fun compareTo(other: EpochMillis): Int =
        toEpochMillis.compareTo(other.toEpochMillis)

    companion object {
        fun of(epochMillis: Long): EpochMillis = EpochMillis(epochMillis)
    }
}
