package com.spendsms.app.domain.subscriptions

import com.spendsms.app.domain.model.SubscriptionFrequency

/**
 * Phase-0 recurring-detection thresholds (Step-2/3 Subscription Detector).
 *
 * Locked as the simplest deterministic local rules that match FR-10:
 * at least two qualifying payments with similar amounts and a recurring interval.
 */
data class SubscriptionDetectionPolicy(
    val minimumOccurrences: Int = 2,
    val amountToleranceRatio: Double = 0.15,
    val weeklyTargetDays: Double = 7.0,
    val weeklySlackDays: Double = 2.0,
    val monthlyTargetDays: Double = 30.0,
    val monthlySlackDays: Double = 5.0,
    val yearlyTargetDays: Double = 365.0,
    val yearlySlackDays: Double = 20.0,
    val inactiveIntervalMultiples: Double = 2.0,
) {
    init {
        require(minimumOccurrences >= 2) { "minimumOccurrences must be >= 2" }
        require(amountToleranceRatio in 0.0..1.0) { "amountToleranceRatio must be in [0,1]" }
        require(weeklySlackDays >= 0.0 && monthlySlackDays >= 0.0 && yearlySlackDays >= 0.0)
        require(inactiveIntervalMultiples > 0.0)
    }

    fun classifyFrequency(medianIntervalDays: Double): SubscriptionFrequency? = when {
        matches(medianIntervalDays, weeklyTargetDays, weeklySlackDays) ->
            SubscriptionFrequency.WEEKLY
        matches(medianIntervalDays, monthlyTargetDays, monthlySlackDays) ->
            SubscriptionFrequency.MONTHLY
        matches(medianIntervalDays, yearlyTargetDays, yearlySlackDays) ->
            SubscriptionFrequency.YEARLY
        else -> null
    }

    fun intervalDays(frequency: SubscriptionFrequency): Double = when (frequency) {
        SubscriptionFrequency.WEEKLY -> weeklyTargetDays
        SubscriptionFrequency.MONTHLY -> monthlyTargetDays
        SubscriptionFrequency.YEARLY -> yearlyTargetDays
        SubscriptionFrequency.UNKNOWN -> monthlyTargetDays
    }

    private fun matches(actual: Double, target: Double, slack: Double): Boolean =
        actual in (target - slack)..(target + slack)

    companion object {
        val Phase0: SubscriptionDetectionPolicy = SubscriptionDetectionPolicy()
        const val DAY_MILLIS: Long = 24L * 60L * 60L * 1_000L
    }
}
