package com.spendsms.app.application.port

import com.spendsms.app.application.analysis.ScanScheduleRequest
import com.spendsms.app.application.analysis.ScanScheduleResult

/**
 * Unique-work scheduler for the local SMS scan. Implementations must not
 * reimplement parsing or persistence; they only enqueue/cancel background work.
 */
interface ScanWorkScheduler {

    suspend fun enqueue(request: ScanScheduleRequest): ScanScheduleResult

    suspend fun cancel()
}
