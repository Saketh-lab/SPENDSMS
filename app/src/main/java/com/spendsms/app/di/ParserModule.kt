package com.spendsms.app.di

import com.spendsms.app.BuildConfig
import com.spendsms.app.application.parser.ParserBundleManager
import com.spendsms.app.application.parser.ParserUpdateService
import com.spendsms.app.application.port.config.RemoteConfigPort
import com.spendsms.app.application.port.parser.ParserUpdateRemotePort
import com.spendsms.app.application.port.parser.ParserUpdateStateStore
import com.spendsms.app.data.config.BundledRemoteConfigPort
import com.spendsms.app.data.parser.AppVersionProvider
import com.spendsms.app.data.parser.AssetParserSigningPublicKeyProvider
import com.spendsms.app.data.parser.BuildConfigAppVersionProvider
import com.spendsms.app.data.parser.DefaultParserBundleManager
import com.spendsms.app.data.parser.ParserBundleClock
import com.spendsms.app.data.parser.ParserSigningPublicKeyProvider
import com.spendsms.app.data.parser.SystemParserBundleClock
import com.spendsms.app.data.parser.update.DataStoreParserUpdateStateStore
import com.spendsms.app.data.parser.update.DefaultParserUpdateService
import com.spendsms.app.data.parser.update.OkHttpParserUpdateRemote
import com.spendsms.app.data.parser.update.ParserCdnAllowList
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
import javax.inject.Named
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
    abstract fun bindParserUpdateService(
        impl: DefaultParserUpdateService,
    ): ParserUpdateService

    @Binds
    @Singleton
    abstract fun bindParserUpdateRemotePort(
        impl: OkHttpParserUpdateRemote,
    ): ParserUpdateRemotePort

    @Binds
    @Singleton
    abstract fun bindParserUpdateStateStore(
        impl: DataStoreParserUpdateStateStore,
    ): ParserUpdateStateStore

    @Binds
    @Singleton
    abstract fun bindRemoteConfigPort(
        impl: BundledRemoteConfigPort,
    ): RemoteConfigPort

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

@Module
@InstallIn(SingletonComponent::class)
object ParserUpdateConfigModule {

    @Provides
    @Singleton
    @Named(OkHttpParserUpdateRemote.PARSER_MANIFEST_URL)
    fun provideParserManifestUrl(): String = BuildConfig.PARSER_MANIFEST_URL

    @Provides
    @Singleton
    fun provideParserCdnAllowList(): ParserCdnAllowList =
        ParserCdnAllowList(
            ParserCdnAllowList.hostsFromUrls(
                BuildConfig.PARSER_CDN_BASE_URL,
                BuildConfig.PARSER_MANIFEST_URL,
            ),
        )
}
