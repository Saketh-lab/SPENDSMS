package com.spendsms.app.di

import com.spendsms.app.application.parser.ParserBundleManager
import com.spendsms.app.data.parser.AppVersionProvider
import com.spendsms.app.data.parser.AssetParserSigningPublicKeyProvider
import com.spendsms.app.data.parser.BuildConfigAppVersionProvider
import com.spendsms.app.data.parser.DefaultParserBundleManager
import com.spendsms.app.data.parser.ParserBundleClock
import com.spendsms.app.data.parser.ParserSigningPublicKeyProvider
import com.spendsms.app.data.parser.SystemParserBundleClock
import com.spendsms.app.domain.filtering.DefaultFinancialMessageFilter
import com.spendsms.app.domain.filtering.FinancialMessageFilter
import com.spendsms.app.domain.parsing.ConfidencePolicy
import com.spendsms.app.domain.parsing.DeclarativeTransactionParser
import com.spendsms.app.domain.parsing.TransactionParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds
    @Singleton
    abstract fun bindParserBundleManager(
        impl: DefaultParserBundleManager,
    ): ParserBundleManager

    @Binds
    @Singleton
    abstract fun bindParserSigningPublicKeyProvider(
        impl: AssetParserSigningPublicKeyProvider,
    ): ParserSigningPublicKeyProvider

    @Binds
    @Singleton
    abstract fun bindAppVersionProvider(
        impl: BuildConfigAppVersionProvider,
    ): AppVersionProvider

    @Binds
    @Singleton
    abstract fun bindParserBundleClock(
        impl: SystemParserBundleClock,
    ): ParserBundleClock

    @Binds
    @Singleton
    abstract fun bindFinancialMessageFilter(
        impl: DefaultFinancialMessageFilter,
    ): FinancialMessageFilter

    @Binds
    @Singleton
    abstract fun bindTransactionParser(
        impl: DeclarativeTransactionParser,
    ): TransactionParser
}

@Module
@InstallIn(SingletonComponent::class)
object ParserPolicyModule {

    @Provides
    @Singleton
    fun provideConfidencePolicy(): ConfidencePolicy = ConfidencePolicy.Phase0
}
