package com.spendsms.app.di

import com.spendsms.app.application.corrections.CorrectionIdFactory
import com.spendsms.app.application.corrections.UuidCorrectionIdFactory
import com.spendsms.app.application.subscriptions.SubscriptionIdFactory
import com.spendsms.app.application.subscriptions.UuidSubscriptionIdFactory
import com.spendsms.app.domain.subscriptions.SubscriptionDetectionPolicy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InsightsModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionIdFactory(
        impl: UuidSubscriptionIdFactory,
    ): SubscriptionIdFactory

    @Binds
    @Singleton
    abstract fun bindCorrectionIdFactory(
        impl: UuidCorrectionIdFactory,
    ): CorrectionIdFactory
}

@Module
@InstallIn(SingletonComponent::class)
object InsightsPolicyModule {

    @Provides
    @Singleton
    fun provideSubscriptionDetectionPolicy(): SubscriptionDetectionPolicy =
        SubscriptionDetectionPolicy.Phase0
}
