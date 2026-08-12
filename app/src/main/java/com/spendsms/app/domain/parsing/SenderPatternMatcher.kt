package com.spendsms.app.domain.parsing

/**
 * Matches SMS sender addresses against declarative [senderPatterns].
 *
 * Patterns are case-insensitive substring matches against the raw sender and a
 * lightly normalized form (strips common Indian DLT prefixes like `VM-`, `AX-`).
 */
object SenderPatternMatcher {

    fun matchesAny(sender: String, patterns: Collection<String>): Boolean {
        if (patterns.isEmpty()) return false
        val candidates = senderVariants(sender)
        return patterns.any { pattern ->
            val needle = pattern.trim()
            if (needle.isEmpty()) return@any false
            candidates.any { it.contains(needle, ignoreCase = true) }
        }
    }

    fun senderVariants(sender: String): List<String> {
        val raw = sender.trim()
        val upper = raw.uppercase()
        val stripped = upper
            .removePrefix("VM-")
            .removePrefix("VK-")
            .removePrefix("AX-")
            .removePrefix("AD-")
            .removePrefix("JX-")
            .removePrefix("JD-")
        return listOf(raw, upper, stripped).distinct()
    }
}
