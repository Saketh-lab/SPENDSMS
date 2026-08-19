package com.spendsms.app.platform.work

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.analysis.MAX_SCAN_BATCH_SIZE
import com.spendsms.app.application.analysis.ScanWorkInput
import com.spendsms.app.application.analysis.ScanWorkOutcome
import com.spendsms.app.application.analysis.ScanWorkRunner
import com.spendsms.app.application.analysis.ScanScheduleRequest
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ScanId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ScanSmsWorkerTest {

    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(2_000L))
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun request_usesExponentialBackoff_andNoNetworkConstraint() {
        val request = ScanWorkContract.createRequest(ScanScheduleRequest(period))
        assertThat(request.workSpec.backoffPolicy).isEqualTo(BackoffPolicy.EXPONENTIAL)
        assertThat(request.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.NOT_REQUIRED)
        assertThat(request.workSpec.expedited).isFalse()
        assertThat(request.workSpec.input.keyValueMap.keys).doesNotContain("body")
        assertThat(request.workSpec.input.keyValueMap.keys).doesNotContain("sms_body")
    }

    @Test
    fun parseInput_roundTripsPeriodAndResumeId_withoutSmsBody() {
        val input = ScanWorkInput(period, resumeScanId = ScanId.of("scan-9"), batchSize = 25)
        val data = ScanWorkContract.toInputData(input)
        assertThat(data.keyValueMap.keys).containsNoneOf("body", "sms", "sms_body")
        assertThat(ScanWorkContract.parseInput(data)).isEqualTo(input)
    }

    @Test
    fun parseInput_coercesOversizedBatchToMax() {
        val data = workDataOf(
            ScanWorkContract.KEY_PERIOD_START to period.start.toEpochMillis,
            ScanWorkContract.KEY_PERIOD_END to period.end.toEpochMillis,
            ScanWorkContract.KEY_BATCH_SIZE to 10_000,
        )
        assertThat(ScanWorkContract.parseInput(data)?.batchSize).isEqualTo(MAX_SCAN_BATCH_SIZE)
    }

    @Test
    fun doWork_success_mapsToResultSuccess() = runBlocking {
        val runner = mockk<ScanWorkRunner>()
        coEvery { runner.run(any()) } returns ScanWorkOutcome.Success(ScanId.of("s1"), "COMPLETED")
        val result = worker(runner).doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        coVerify { runner.run(ScanWorkInput(period)) }
    }

    @Test
    fun doWork_retryOutcome_mapsToResultRetry() = runBlocking {
        val runner = mockk<ScanWorkRunner>()
        coEvery { runner.run(any()) } returns ScanWorkOutcome.Retry(ScanId.of("s1"), "PROVIDER_ERROR")
        val result = worker(runner).doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
    }

    @Test
    fun doWork_permissionFailure_mapsToResultFailure() = runBlocking {
        val runner = mockk<ScanWorkRunner>()
        coEvery { runner.run(any()) } returns ScanWorkOutcome.Failure(null, "PERMISSION_DENIED")
        val result = worker(runner).doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        val failure = result as ListenableWorker.Result.Failure
        assertThat(failure.outputData.getString(ScanWorkContract.KEY_REASON))
            .isEqualTo("PERMISSION_DENIED")
    }

    @Test
    fun doWork_invalidInput_failsWithoutCallingRunner() = runBlocking {
        val runner = mockk<ScanWorkRunner>(relaxed = true)
        val worker = TestListenableWorkerBuilder<ScanSmsWorker>(context)
            .setInputData(workDataOf("junk" to "value"))
            .setWorkerFactory(factory(runner))
            .build()
        val result = worker.doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        coVerify(exactly = 0) { runner.run(any()) }
    }

    private fun worker(runner: ScanWorkRunner): ScanSmsWorker {
        return TestListenableWorkerBuilder<ScanSmsWorker>(context)
            .setInputData(ScanWorkContract.toInputData(ScanWorkInput(period)))
            .setWorkerFactory(factory(runner))
            .build()
    }

    private fun factory(runner: ScanWorkRunner): WorkerFactory {
        return object : WorkerFactory() {
            override fun createWorker(
                appContext: android.content.Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return ScanSmsWorker(appContext, workerParameters, runner)
            }
        }
    }
}
