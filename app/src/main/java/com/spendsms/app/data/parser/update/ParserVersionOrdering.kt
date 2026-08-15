package com.spendsms.app.data.parser.update

/**
 * Orders parser version strings for upgrade / anti-downgrade checks.
 *
 * Uses the trailing dotted numeric segment when present (e.g. `bundled-2026.08.12.0`
 * → `2026.08.12.0`) so bundled and remote labels remain comparable.
 */
object ParserVersionOrdering {

    fun compare(a: String, b: String): Int {
        val left = numericCore(a)
        val right = numericCore(b)
        if (left != null && right != null) {
            val len = maxOf(left.size, right.size)
            for (i in 0 until len) {
                val l = left.getOrElse(i) { 0 }
                val r = right.getOrElse(i) { 0 }
                if (l != r) return l.compareTo(r)
            }
            return 0
        }
        return a.compareTo(b)
    }

    fun isNewer(candidate: String, baseline: String): Boolean =
        compare(candidate, baseline) > 0

    fun isNewerOrEqual(candidate: String, baseline: String): Boolean =
        compare(candidate, baseline) >= 0

    private fun numericCore(version: String): List<Int>? {
        val match = NUMERIC_SUFFIX.find(version.trim()) ?: return null
        val parts = match.value.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() }
    }

    private val NUMERIC_SUFFIX = Regex("""\d+(?:\.\d+)+""")
}
