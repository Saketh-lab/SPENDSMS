package com.spendsms.app.domain.merchant

import com.spendsms.app.domain.model.MerchantKey

/**
 * Bundled alias from a normalized lookup token to a stable merchant identity.
 */
data class MerchantAlias(
    val key: MerchantKey,
    val displayName: String,
    val tokens: Set<String>,
) {
    init {
        require(displayName.isNotBlank()) { "alias displayName must not be blank" }
        require(tokens.isNotEmpty()) { "alias tokens must not be empty" }
        require(tokens.all { it.isNotBlank() }) { "alias tokens must not contain blanks" }
    }
}
