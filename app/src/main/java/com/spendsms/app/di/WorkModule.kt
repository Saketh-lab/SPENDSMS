package com.spendsms.app.di

import com.spendsms.app.application.port.ScanWorkScheduler
import com.spendsms.app.platform.work.WorkManagerScanScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    @Singleton
    abstract fun bindScanWorkScheduler(
        impl: WorkManagerScanScheduler,
    ): ScanWorkScheduler
}
