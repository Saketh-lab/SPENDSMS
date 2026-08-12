package com.spendsms.app.platform.work

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.analysis.ScanCoordinator
import com.spendsms.app.application.analysis.ScanScheduleRequest
import com.spendsms.app.application.analysis.ScanScheduleResult
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.domain.model.ScanStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WorkManagerScanSchedulerTest {

    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(2_000L))
    private lateinit var context: Application
    private lateinit var coordinator: ScanCoordinator
    private lateinit var scans: ScanStateRepository
    private lateinit var scheduler: WorkManagerScanScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        startedLatch.set(CountDownLatch(1))
        hold.set(true)
        coordinator = mockk(relaxed = true)
        scans = mockk(relaxed = true)
        initializeWorkManager(context)
        scheduler = WorkManagerScanScheduler(context, coordinator, scans)
    }

    @After
    fun tearDown() {
        hold.set(false)
        WorkManager.getInstance(context).cancelUniqueWork(ScanWorkContract.UNIQUE_WORK_NAME)
    }

    @Test
    fun enqueue_duplicateWhileRunning_keepsExistingWork() = runBlocking {
        val first = scheduler.enqueue(ScanScheduleRequest(period))
        assertThat(first).isInstanceOf(ScanScheduleResult.Enqueued::class.java)
        assertThat(startedLatch.get().await(5, TimeUnit.SECONDS)).isTrue()

        val second = scheduler.enqueue(ScanScheduleRequest(period))
        assertThat(second).isInstanceOf(ScanScheduleResult.AlreadyScheduled::class.java)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ScanWorkContract.UNIQUE_WORK_NAME)
            .get()
        assertThat(infos).hasSize(1)
        assertThat(infos.single().state).isEqualTo(WorkInfo.State.RUNNING)
    }

    @Test
    fun cancel_requestsCoordinatorCancel_andStopsUniqueWork() = runBlocking {
        val scanId = ScanId.of("scan-1")
        coEvery { scans.findResumable() } returns ScanState(
            id = scanId,
            period = period,
            lastProcessedMessageId = "3",
            parserVersion = ParserVersion.of("v1"),
            status = ScanStatus.RUNNING,
            processedCount = 3,
            acceptedCount = 1,
            startedAt = EpochMillis.of(10L),
            completedAt = null,
            updatedAt = EpochMillis.of(11L),
        )
        scheduler.enqueue(ScanScheduleRequest(period))
        assertThat(startedLatch.get().await(5, TimeUnit.SECONDS)).isTrue()

        scheduler.cancel()

        verify { coordinator.requestCancel(scanId) }
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ScanWorkContract.UNIQUE_WORK_NAME)
            .get()
        assertThat(infos.single().state.isFinished).isTrue()
    }

    private fun initializeWorkManager(context: Context) {
        val config = Configuration.Builder()
            .setExecutor(Executors.newSingleThreadExecutor())
            .setTaskExecutor(Executors.newSingleThreadExecutor())
            .setWorkerFactory(blockingFactory())
            .build()
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        } catch (_: IllegalStateException) {
            // Process-level WorkManager already initialized for this test class.
        }
    }

    private fun blockingFactory(): WorkerFactory {
        return object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return object : CoroutineWorker(appContext, workerParameters) {
                    override suspend fun doWork(): Result {
                        startedLatch.get().countDown()
                        while (hold.get() && currentCoroutineContext().isActive) {
                            delay(20)
                        }
                        return Result.success()
                    }
                }
            }
        }
    }

    companion object {
        private val startedLatch = AtomicReference(CountDownLatch(1))
        private val hold = AtomicBoolean(true)
    }
}
