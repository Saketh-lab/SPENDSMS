package com.spendsms.app.application.port

/**
 * Atomic deletion of user-controlled analysed financial data (Step-3 §3.15).
 *
 * Must cover transactions, corrections, subscriptions, scan state, and any
 * derived dashboard cache. Parser/config packages are out of scope unless a
 * separate full-reset capability is added later.
 */
interface FinancialDataDeletionPort {

    suspend fun deleteAllAnalysedData()
}
