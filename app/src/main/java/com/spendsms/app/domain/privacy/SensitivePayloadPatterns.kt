package com.spendsms.app.domain.privacy

/**
 * Conservative detectors for content that must never appear in logs, telemetry,
 * WorkManager Data, or support-upload templates (Step-3 privacy tests / Step-4).
 */
object SensitivePayloadPatterns {

    private val FORBIDDEN: List<Regex> = listOf(
        Regex("""(?i)rs\.?\s*[\d,]+\.?\d*"""),
        Regex("""(?i)\binr\s*[\d,]+\.?\d*"""),
        Regex("""\d{12,}"""),
        Regex("""(?i)\botp\s*(is|:)?\s*\d{4,8}"""),
        Regex("""(?i)\ba/?c\s*(xx)?\d{2,}"""),
        Regex("""(?i)upi\s*ref"""),
        Regex("""(?i)\S+@(oksbi|okhdfc|okicici|ybl|axl|ibl)\b"""),
    )

    fun looksLikeUnredactedSmsOrFinancialSecret(text: String): Boolean =
        FORBIDDEN.any { it.containsMatchIn(text) }
}
