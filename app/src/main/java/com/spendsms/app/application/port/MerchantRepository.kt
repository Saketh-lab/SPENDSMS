package com.spendsms.app.application.port

import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.MerchantKey

/**
 * Merchant alias / lookup port.
 *
 * Step-3 does not require an independent merchant table in Phase-0; this port
 * exposes capability for rule-backed and correction-backed merchant resolution
 * without implying cloud merchant sync.
 */
interface MerchantRepository {

    suspend fun findByKey(key: MerchantKey): Merchant?

    /**
     * Resolve a stable merchant from normalised parser text and optional institution.
     * Must always return a local [Merchant] (fallback key/display allowed).
     */
    suspend fun resolve(
        rawNormalized: String,
        institution: String?,
    ): Merchant
}
