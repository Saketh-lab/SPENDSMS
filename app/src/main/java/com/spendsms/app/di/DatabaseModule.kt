package com.spendsms.app.di

import android.content.Context
import com.spendsms.app.data.room.SpendSmsDatabase
import com.spendsms.app.data.room.dao.CategoryDao
import com.spendsms.app.data.room.dao.DashboardCacheDao
import com.spendsms.app.data.room.dao.ParserMetadataDao
import com.spendsms.app.data.room.dao.ScanStateDao
import com.spendsms.app.data.room.dao.SubscriptionDao
import com.spendsms.app.data.room.dao.TransactionDao
import com.spendsms.app.data.room.dao.UserCorrectionDao
import com.spendsms.app.platform.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSpendSmsDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): SpendSmsDatabase =
        SpendSmsDatabase.buildEncrypted(
            context = context,
            passphrase = keyProvider.getOrCreatePassphrase(),
        )

    @Provides
    fun provideCategoryDao(db: SpendSmsDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: SpendSmsDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideUserCorrectionDao(db: SpendSmsDatabase): UserCorrectionDao = db.userCorrectionDao()

    @Provides
    fun provideSubscriptionDao(db: SpendSmsDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideScanStateDao(db: SpendSmsDatabase): ScanStateDao = db.scanStateDao()

    @Provides
    fun provideParserMetadataDao(db: SpendSmsDatabase): ParserMetadataDao = db.parserMetadataDao()

    @Provides
    fun provideDashboardCacheDao(db: SpendSmsDatabase): DashboardCacheDao = db.dashboardCacheDao()
}
