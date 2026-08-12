package com.spendsms.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * WorkManager is configured through [Configuration.Provider] so Hilt can
 * inject [androidx.hilt.work.HiltWorker] implementations such as the SMS scan worker.
 */
@HiltAndroidApp
class SpendSmsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
