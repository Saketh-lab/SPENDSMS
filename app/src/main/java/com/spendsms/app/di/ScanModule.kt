package com.spendsms.app.di

import com.spendsms.app.application.analysis.ScanClock
import com.spendsms.app.application.analysis.ScanIdFactory
import com.spendsms.app.application.analysis.SystemScanClock
import com.spendsms.app.application.analysis.UuidScanIdFactory
import com.spendsms.app.application.port.sms.SmsMessageSource
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.data.sms.AndroidSmsMessageSource
import com.spendsms.app.platform.permissions.AndroidSmsPermissionPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

    @Binds
    @Singleton
    abstract fun bindSmsMessageSource(
        impl: AndroidSmsMessageSource,
    ): SmsMessageSource

    @Binds
    @Singleton
    abstract fun bindSmsPermissionPort(
        impl: AndroidSmsPermissionPort,
    ): SmsPermissionPort

    @Binds
    @Singleton
    abstract fun bindScanClock(
        impl: SystemScanClock,
    ): ScanClock

    @Binds
    @Singleton
    abstract fun bindScanIdFactory(
        impl: UuidScanIdFactory,
    ): ScanIdFactory
}
