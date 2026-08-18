package com.spendsms.app.application.analysis

import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.subscriptions.SubscriptionDetectionService
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared post-scan work so UI and WorkManager completions stay consistent.
 * Recomputes subscriptions and dashboard from Room (the financial system of record).
 */
@Singleton
class ScanCompletionHandler @Inject constructor(
    private val preferences: UserPreferencesStore,
    private val subscriptionDetectionService: SubscriptionDetectionService,
    private val dashboardService: DashboardService,
) {

    suspend fun onScanCompleted(period: AnalysisPeriod, now: EpochMillis) {
        preferences.setLastAnalysisPeriod(period)
        subscriptionDetectionService.detectAndPersist(period, now)
        dashboardService.recomputeAndCache(period)
    }
}
