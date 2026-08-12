package com.spendsms.app.di

import com.spendsms.app.application.analysis.TransactionIdFactory
import com.spendsms.app.application.analysis.UuidTransactionIdFactory
import com.spendsms.app.domain.deduplication.DuplicatePolicy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeduplicationModule {

    @Binds
    @Singleton
    abstract fun bindTransactionIdFactory(
        impl: UuidTransactionIdFactory,
    ): TransactionIdFactory
}

@Module
@InstallIn(SingletonComponent::class)
object DeduplicationPolicyModule {

    @Provides
    @Singleton
    fun provideDuplicatePolicy(): DuplicatePolicy = DuplicatePolicy.Phase0
}
