package com.spendsms.app.domain.filtering

/**
 * Bundled Phase-0 filter lexicon (data-only keyword signals).
 *
 * Not a second parser: positives only gate deeper declarative rule evaluation.
 */
object BundledFilterLexicon {

    val strongReject: List<Regex> = listOf(
        Regex("""(?i)\b(otp|one[-\s]?time\s+password|verification\s+code|auth(?:entication)?\s+code)\b"""),
        Regex("""(?i)\byour\s+(login|signin|sign-in)\s+code\b"""),
        Regex("""(?i)\bdo\s+not\s+share\s+(this\s+)?(otp|code)\b"""),
    )

    val marketingReject: List<Regex> = listOf(
        Regex("""(?i)\b(unsubscribe|click\s+here|limited\s+period\s+offer|flat\s+\d+%\s+off)\b"""),
        Regex("""(?i)\b(congrat(?:ulation)?s!?)\b.*\b(won|winner|lottery|prize)\b"""),
    )

    val deliveryReject: List<Regex> = listOf(
        Regex("""(?i)\b(out\s+for\s+delivery|package\s+delivered|tracking\s+id)\b"""),
    )

    /** Signals that the body is worth attempting declarative parse. */
    val financialPositive: List<Regex> = listOf(
        Regex("""(?i)\b(debited|credited|spent|withdrawn|purchase|payment|refunded|reversed|transferred)\b"""),
        Regex("""(?i)\b(upi|neft|imps|rtgs|a/?c\s+\*|xx+\d{2,})\b"""),
        Regex("""(?i)(?:rs\.?|inr|₹)\s*[0-9]"""),
    )
}
