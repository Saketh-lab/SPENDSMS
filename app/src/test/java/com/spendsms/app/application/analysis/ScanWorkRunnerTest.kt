package com.spendsms.app.application.analysis

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.domain.model.ScanStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ScanWorkRunnerTest {

    private val period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(9_000L))
    private val coordinator = mockk<ScanCoordinator>()
    private val scans = mockk<ScanStateRepository>()
    private val permission = mockk<SmsPermissionPort>()

    private val runner = ScanWorkRunner(
        scanCoordinator = coordinator,
        scanStateRepository = scans,
        permissionPort = permission,
    )

    @Test
    fun permissionDenied_failsWithoutStartingScan() = runBlocking {
        coEvery { permission.hasReadSmsPermission() } returns false

        val outcome = runner.run(ScanWorkInput(period))

        assertThat(outcome).isEqualTo(
            ScanWorkOutcome.Failure(scanId = null, reason = "PERMISSION_DENIED"),
        )
        coVerify(exactly = 0) { coordinator.startScan(any()) }
    }

    @Test
    fun success_delegatesToCoordinator() = runBlocking {
        coEvery { permission.hasReadSmsPermission() } returns true
        coEvery { scans.findResumable() } returns null
        val completed = sampleState(ScanStatus.COMPLETED, completedAt = EpochMillis.of(20L))
        coEvery { coordinator.startScan(any()) } returns ScanResult.Completed(completed, 0)

        val outcome = runner.run(ScanWorkInput(period))

        assertThat(outcome).isInstanceOf(ScanWorkOutcome.Success::class.java)
        coVerify {
            coordinator.startScan(
                ScanRequest(period = period, resumeScanId = null, batchSize = DEFAULT_SCAN_BATCH_SIZE),
            )
        }
    }

    @Test
    fun resume_usesPersistedInterruptedScan() = runBlocking {
        coEvery { permission.hasReadSmsPermission() } returns true
        val interrupted = sampleState(ScanStatus.INTERRUPTED, lastId = "77")
        coEvery { scans.findResumable() } returns interrupted
        coEvery { coordinator.startScan(any()) } returns ScanResult.Completed(
            interrupted.copy(
                status = ScanStatus.COMPLETED,
                completedAt = EpochMillis.of(20L),
                updatedAt = EpochMillis.of(20L),
            ),
            0,
        )

        runner.run(ScanWorkInput(period))

        coVerify {
            coordinator.startScan(
                match { it.resumeScanId == interrupted.id && it.period == period },
            )
        }
    }

    @Test
    fun resume_prefersExplicitScanId() = runBlocking {
        coEvery { permission.hasReadSmsPermission() } returns true
        val explicit = ScanId.of("explicit")
        coEvery { coordinator.startScan(any()) } returns ScanResult.Failed(
            null,
            ScanFailureReason.NOT_RESUMABLE,
            "gone",
        )

        runner.run(ScanWorkInput(period, resumeScanId = explicit))

        coVerify { coordinator.startScan(match { it.resumeScanId == explicit }) }
        coVerify(exactly = 0) { scans.findResumable() }
    }

    @Test
    fun providerError_mapsToRetry() = runBlocking {
        coEvery { permission.hasReadSmsPermission() } returns true
        coEvery { scans.findResumable() } returns null
        coEvery { coordinator.startScan(any()) } returns ScanResult.Interrupted(
            sampleState(ScanStatus.INTERRUPTED),
            0,
            ScanInterruptReason.PROVIDER_ERROR,
        )

        val outcome = runner.run(ScanWorkInput(period))

        assertThat(outcome).isInstanceOf(ScanWorkOutcome.Retry::class.java)
        assertThat((outcome as ScanWorkOutcome.Retry).reason).isEqualTo("PROVIDER_ERROR")
    }

    @Test
    fun permissionRevoked_mapsToFailure() = runBlocking {
        coEvery { permission.hasReadSmsPermission() } returns true
        coEvery { scans.findResumable() } returns null
        coEvery { coordinator.startScan(any()) } returns ScanResult.Interrupted(
            sampleState(ScanStatus.INTERRUPTED),
            0,
            ScanInterruptReason.PERMISSION_REVOKED,
        )

        val outcome = runner.run(ScanWorkInput(period))

        assertThat(outcome).isEqualTo(
            ScanWorkOutcome.Failure(scanId = ScanId.of("scan-1"), reason = "PERMISSION_REVOKED"),
        )
    }

    @Test
    fun cancelled_mapsToSuccess() = runBlocking {
        coEvery { permission.hasReadSmsPermission() } returns true
        coEvery { scans.findResumable() } returns null
        coEvery { coordinator.startScan(any()) } returns ScanResult.Cancelled(
            sampleState(ScanStatus.CANCELLED, completedAt = EpochMillis.of(20L)),
            0,
        )

        val outcome = runner.run(ScanWorkInput(period))

        assertThat((outcome as ScanWorkOutcome.Success).status).isEqualTo("CANCELLED")
    }

    private fun sampleState(
        status: ScanStatus,
        lastId: String? = "1",
        completedAt: EpochMillis? = null,
    ): ScanState = ScanState(
        id = ScanId.of("scan-1"),
        period = period,
        lastProcessedMessageId = lastId,
        parserVersion = ParserVersion.of("v1"),
        status = status,
        processedCount = 1,
        acceptedCount = 1,
        startedAt = EpochMillis.of(10L),
        completedAt = completedAt,
        updatedAt = completedAt ?: EpochMillis.of(11L),
    )
}
