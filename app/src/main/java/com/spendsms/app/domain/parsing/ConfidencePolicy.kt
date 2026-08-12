package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.Confidence

/**
 * Phase-0 confidence policy (Step-3 open decision locked here).
 *
 * Candidates below [minimumEmitConfidence] are not emitted as successes.
 */
data class ConfidencePolicy(
    val baseMatch: Double = 0.50,
    val senderMatchedBonus: Double = 0.25,
    val merchantBonus: Double = 0.10,
    val referenceBonus: Double = 0.08,
    val explicitCurrencyBonus: Double = 0.04,
    val explicitDateBonus: Double = 0.03,
    val minimumEmitConfidence: Double = 0.60,
) {
    fun score(
        senderMatched: Boolean,
        hasMerchant: Boolean,
        hasReference: Boolean,
        hasExplicitCurrency: Boolean,
        hasExplicitDate: Boolean,
    ): Confidence {
        var value = baseMatch
        if (senderMatched) value += senderMatchedBonus
        if (hasMerchant) value += merchantBonus
        if (hasReference) value += referenceBonus
        if (hasExplicitCurrency) value += explicitCurrencyBonus
        if (hasExplicitDate) value += explicitDateBonus
        return Confidence.of(value.coerceIn(Confidence.MIN, Confidence.MAX))
    }

    companion object {
        val Phase0: ConfidencePolicy = ConfidencePolicy()
    }
}
