package com.spendsms.app.domain.deduplication

/**
 * Phase-0 duplicate windows (Step-3 open timing locked here).
 *
 * Fingerprint buckets make same-alert rescans and tightly clustered alerts
 * share an idempotency key. The wider near-window flags ambiguous second
 * records as [com.spendsms.app.domain.model.DuplicateStatus.SUSPECTED]
 * without merging them.
 */
data class DuplicatePolicy(
    val fingerprintBucketMillis: Long = 5L * 60L * 1_000L,
    val nearWindowMillis: Long = 30L * 60L * 1_000L,
) {
    init {
        require(fingerprintBucketMillis > 0L) { "fingerprintBucketMillis must be > 0" }
        require(nearWindowMillis >= fingerprintBucketMillis) {
            "nearWindowMillis must be >= fingerprintBucketMillis"
        }
    }

    fun bucket(epochMillis: Long): Long =
        (epochMillis / fingerprintBucketMillis) * fingerprintBucketMillis

    companion object {
        val Phase0: DuplicatePolicy = DuplicatePolicy()
    }
}
