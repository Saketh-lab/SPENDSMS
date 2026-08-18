package com.spendsms.app.di

import com.spendsms.app.application.port.support.SupportSubmissionPort
import com.spendsms.app.application.port.telemetry.TelemetryPort
import com.spendsms.app.data.support.UnavailableSupportSubmissionPort
import com.spendsms.app.data.telemetry.NoOpTelemetryPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase-0 control-plane ports.
 *
 * Local-only adapters are bound by default. When AWS/CDN endpoints are configured
 * in BuildConfig, swap bindings to network implementations without changing callers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ControlPlaneModule {

    @Binds
    @Singleton
    abstract fun bindTelemetryPort(
        impl: NoOpTelemetryPort,
    ): TelemetryPort

    @Binds
    @Singleton
    abstract fun bindSupportSubmissionPort(
        impl: UnavailableSupportSubmissionPort,
    ): SupportSubmissionPort
}
