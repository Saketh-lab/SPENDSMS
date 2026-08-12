package com.spendsms.app.data.repository

import androidx.room.withTransaction
import com.spendsms.app.application.port.FinancialDataDeletionPort
import com.spendsms.app.data.room.SpendSmsDatabase
import com.spendsms.app.data.room.dao.DashboardCacheDao
import com.spendsms.app.data.room.dao.ScanStateDao
import com.spendsms.app.data.room.dao.SubscriptionDao
import com.spendsms.app.data.room.dao.TransactionDao
import com.spendsms.app.data.room.dao.UserCorrectionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atomic analysed-data wipe (Step-3 §3.15). Parser/config packages are retained.
 */
@Singleton
class RoomFinancialDataDeletionPort @Inject constructor(
    private val db: SpendSmsDatabase,
    private val subscriptionDao: SubscriptionDao,
    private val userCorrectionDao: UserCorrectionDao,
    private val transactionDao: TransactionDao,
    private val scanStateDao: ScanStateDao,
    private val dashboardCacheDao: DashboardCacheDao,
) : FinancialDataDeletionPort {

    override suspend fun deleteAllAnalysedData() {
        db.withTransaction {
            subscriptionDao.deleteAll()
            userCorrectionDao.deleteAll()
            transactionDao.deleteAll()
            scanStateDao.deleteAll()
            dashboardCacheDao.deleteAll()
        }
    }
}
