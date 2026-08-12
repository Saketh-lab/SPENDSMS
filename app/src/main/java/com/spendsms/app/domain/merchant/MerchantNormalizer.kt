package com.spendsms.app.domain.merchant

import com.spendsms.app.domain.model.MerchantKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic merchant/recipient normalization (Step-3 Merchant Normalizer).
 *
 * Never blocks classification: unknown values fall back to cleaned parser text.
 * [institution] is retained for call-site compatibility; Phase-0 keys are
 * merchant-stable and not institution-prefixed so aliases and future-apply
 * corrections match across banks.
 */
@Singleton
class MerchantNormalizer @Inject constructor() {

    fun normalize(raw: String?, institution: String? = null): Merchant? {
        if (raw.isNullOrBlank()) return null
        val originalClean = collapseWhitespace(raw)
        if (originalClean.isEmpty()) return null

        val stripped = stripProcessorPrefix(originalClean)
        val withoutHandle = stripUpiHandle(stripped)
        val displaySource = collapseWhitespace(withoutHandle).ifEmpty { originalClean }
        val keyToken = foldKey(displaySource)
        if (keyToken.isEmpty()) {
            return Merchant(
                key = MerchantKey.of(UNKNOWN_KEY),
                displayName = originalClean,
                rawNormalized = originalClean,
            )
        }

        val alias = BundledMerchantAliases.byToken[keyToken]
        return Merchant(
            key = alias?.key ?: MerchantKey.of(keyToken),
            displayName = alias?.displayName ?: displaySource,
            rawNormalized = originalClean,
        )
    }

    companion object {
        const val UNKNOWN_KEY: String = "unknown"

        private val PROCESSOR_PREFIX = Regex(
            """(?i)^(paytm|ph|mm|az|gpay|googlepay|bharatpe|razorpay|freecharge|mobikwik)\s*[\*\-/:]+\s*""",
        )
        private val WWW_PREFIX = Regex("""(?i)^www\.""")
        private val NON_KEY_CHARS = Regex("""[^a-z0-9]+""")
        private val MULTI_SPACE = Regex("""\s+""")

        internal fun collapseWhitespace(value: String): String =
            value.trim().replace(MULTI_SPACE, " ")

        internal fun stripProcessorPrefix(value: String): String {
            var current = WWW_PREFIX.replace(value, "")
            current = PROCESSOR_PREFIX.replace(current, "")
            return current.trim()
        }

        internal fun stripUpiHandle(value: String): String {
            val at = value.indexOf('@')
            if (at <= 0) return value
            return value.substring(0, at).trim()
        }

        internal fun foldKey(value: String): String =
            value.lowercase()
                .replace(NON_KEY_CHARS, " ")
                .trim()
                .replace(MULTI_SPACE, " ")
    }
}
