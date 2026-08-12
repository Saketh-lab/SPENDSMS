package com.spendsms.app.application.port

import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState

/**
 * Resumable scan progress store (Step-3 `scan_state`).
 *
 * Application policy permits at most one active scan at a time.
 */
interface ScanStateRepository {

    suspend fun findById(id: ScanId): ScanState?

    /** Returns PENDING or RUNNING scan if one exists. */
    suspend fun findActive(): ScanState?

    suspend fun findLatestCompleted(): ScanState?

    suspend fun save(state: ScanState): ScanState

    suspend fun deleteAll()
}
