package com.spendsms.app.di

import com.spendsms.app.application.port.CategoryRepository
import com.spendsms.app.application.port.DashboardCachePort
import com.spendsms.app.application.port.FinancialDataDeletionPort
import com.spendsms.app.application.port.MerchantRepository
import com.spendsms.app.application.port.ParserBundleRepository
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.SubscriptionRepository
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.application.port.UserCorrectionRepository
import com.spendsms.app.data.repository.RoomCategoryRepository
import com.spendsms.app.data.repository.RoomDashboardCachePort
import com.spendsms.app.data.repository.RoomFinancialDataDeletionPort
import com.spendsms.app.data.repository.RoomMerchantRepository
import com.spendsms.app.data.repository.RoomParserBundleRepository
import com.spendsms.app.data.repository.RoomScanStateRepository
import com.spendsms.app.data.repository.RoomSubscriptionRepository
import com.spendsms.app.data.repository.RoomTransactionRepository
import com.spendsms.app.data.repository.RoomUserCorrectionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: RoomTransactionRepository,
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: RoomCategoryRepository,
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindMerchantRepository(
        impl: RoomMerchantRepository,
    ): MerchantRepository

    @Binds
    @Singleton
    abstract fun bindUserCorrectionRepository(
        impl: RoomUserCorrectionRepository,
    ): UserCorrectionRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: RoomSubscriptionRepository,
    ): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindScanStateRepository(
        impl: RoomScanStateRepository,
    ): ScanStateRepository

    @Binds
    @Singleton
    abstract fun bindParserBundleRepository(
        impl: RoomParserBundleRepository,
    ): ParserBundleRepository

    @Binds
    @Singleton
    abstract fun bindFinancialDataDeletionPort(
        impl: RoomFinancialDataDeletionPort,
    ): FinancialDataDeletionPort

    @Binds
    @Singleton
    abstract fun bindDashboardCachePort(
        impl: RoomDashboardCachePort,
    ): DashboardCachePort
}
