package com.spendsms.app.application.port

import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.domain.model.AnalysisPeriod

/**
 * Optional derived dashboard cache (Step-3 optional `dashboard_cache`).
 *
 * Cache failures must never alter transaction data; callers may recompute.
 */
interface DashboardCachePort {

    suspend fun get(period: AnalysisPeriod): DashboardResult?

    suspend fun put(result: DashboardResult)

    suspend fun invalidateAll()
}
