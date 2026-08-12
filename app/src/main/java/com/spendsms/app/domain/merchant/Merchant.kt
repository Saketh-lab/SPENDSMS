package com.spendsms.app.domain.merchant

import com.spendsms.app.domain.model.MerchantKey

/**
 * Local merchant representation after normalization.
 *
 * Does not contain raw SMS body — only normalized extracted merchant fields.
 */
data class Merchant(
    val key: MerchantKey,
    val displayName: String,
    val rawNormalized: String? = null,
) {
    init {
        require(displayName.isNotBlank()) { "Merchant displayName must not be blank" }
        rawNormalized?.let {
            require(it.isNotBlank()) { "Merchant rawNormalized, when present, must not be blank" }
        }
    }
}
