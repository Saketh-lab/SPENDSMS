package com.spendsms.app.presentation.scan

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.analysis.ScanCompletionHandler
import com.spendsms.app.application.analysis.ScanCoordinator
import com.spendsms.app.application.analysis.ScanFailureReason
import com.spendsms.app.application.analysis.ScanProgressListener
import com.spendsms.app.application.analysis.ScanRequest
import com.spendsms.app.application.analysis.ScanResult
import com.spendsms.app.application.analysis.ScanScheduleRequest
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.ScanWorkScheduler
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.domain.model.ScanStatus
import com.spendsms.app.presentation.common.PeriodPreset
import com.spendsms.app.presentation.common.toAnalysisPeriod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startScan_withoutPermission_requiresPermission() = runTest {
        val coordinator = mockk<ScanCoordinator>(relaxed = true)
        val vm = viewModel(
            coordinator = coordinator,
            permission = SmsPermissionPort { false },
        )
        vm.startSelectedPeriod()
        assertThat(vm.phase.value).isInstanceOf(ScanPhase.PermissionRequired::class.java)
        coVerify(exactly = 0) {
            coordinator.startScan(
                request = match { true },
                onProgress = any(),
            )
        }
    }

    @Test
    fun startScan_success_movesToCompleted_andRunsPostProcessing() = runTest {
        val coordinator = mockk<ScanCoordinator>()
        val preferences = mockk<UserPreferencesStore>(relaxed = true)
        val dashboard = mockk<DashboardService>(relaxed = true)
        val completion = ScanCompletionHandler(
            preferences = preferences,
            subscriptionDetectionService = mockk(relaxed = true),
            dashboardService = dashboard,
        )
        val scheduler = mockk<ScanWorkScheduler>(relaxed = true)
        val requestSlot = slot<ScanRequest>()
        coEvery {
            coordinator.startScan(
                request = capture(requestSlot),
                onProgress = any<ScanProgressListener>(),
            )
        } answers {
            ScanResult.Completed(
                state = completedState(requestSlot.captured.period),
                isolatedFailureCount = 1,
            )
        }

        val vm = viewModel(
            coordinator = coordinator,
            permission = SmsPermissionPort { true },
            preferences = preferences,
            completion = completion,
            scheduler = scheduler,
        )
        vm.selectPreset(PeriodPreset.LAST_30_DAYS)
        vm.startSelectedPeriod()
        dispatcher.scheduler.advanceUntilIdle()

        val completed = vm.phase.value as ScanPhase.Completed
        assertThat(completed.acceptedCount).isEqualTo(4)
        assertThat(completed.isolatedFailures).isEqualTo(1)
        assertThat(requestSlot.captured.resumeScanId).isNull()
        coVerify(exactly = 1) { preferences.setLastAnalysisPeriod(requestSlot.captured.period) }
        coVerify(exactly = 1) { dashboard.recomputeAndCache(requestSlot.captured.period) }
        coVerify { scheduler.enqueue(match { it.period == requestSlot.captured.period }) }
    }

    @Test
    fun leftoverResumable_autoResumesOnInit() = runTest {
        val leftoverPeriod = PeriodPreset.LAST_30_DAYS.toAnalysisPeriod()
        val leftover = ScanState(
            id = ScanId.of("resume-1"),
            period = leftoverPeriod,
            lastProcessedMessageId = "4",
            parserVersion = ParserVersion.of("v1"),
            status = ScanStatus.INTERRUPTED,
            processedCount = 4,
            acceptedCount = 2,
            startedAt = EpochMillis.of(1L),
            completedAt = null,
            updatedAt = EpochMillis.of(2L),
        )
        val coordinator = mockk<ScanCoordinator>()
        val scans = mockk<ScanStateRepository>()
        coEvery { scans.findResumable() } returns leftover
        coEvery {
            coordinator.startScan(
                request = match { it.resumeScanId == leftover.id },
                onProgress = any(),
            )
        } returns ScanResult.Completed(
            leftover.copy(
                status = ScanStatus.COMPLETED,
                processedCount = 9,
                acceptedCount = 4,
                completedAt = EpochMillis.of(3L),
                updatedAt = EpochMillis.of(3L),
            ),
            0,
        )
        val vm = viewModel(
            coordinator = coordinator,
            permission = SmsPermissionPort { true },
            scans = scans,
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.phase.value).isInstanceOf(ScanPhase.Completed::class.java)
        coVerify {
            coordinator.startScan(
                request = match { it.resumeScanId == leftover.id },
                onProgress = any(),
            )
        }
    }

    @Test
    fun startScan_failure_mapsPermissionDenied() = runTest {
        val coordinator = mockk<ScanCoordinator>()
        coEvery {
            coordinator.startScan(
                request = match { true },
                onProgress = any(),
            )
        } returns ScanResult.Failed(
            state = null,
            reason = ScanFailureReason.PERMISSION_DENIED,
            detail = "denied",
        )
        val vm = viewModel(coordinator = coordinator, permission = SmsPermissionPort { true })
        vm.startSelectedPeriod()
        dispatcher.scheduler.advanceUntilIdle()
        val failed = vm.phase.value as ScanPhase.Failed
        assertThat(failed.message).contains("permission")
    }

    private fun viewModel(
        coordinator: ScanCoordinator,
        permission: SmsPermissionPort,
        preferences: UserPreferencesStore = mockk(relaxed = true),
        scans: ScanStateRepository = mockk {
            coEvery { findResumable() } returns null
        },
        completion: ScanCompletionHandler = mockk(relaxed = true),
        scheduler: ScanWorkScheduler = mockk(relaxed = true),
    ): ScanViewModel {
        every { preferences.lastAnalysisPeriod } returns flowOf(null)
        return ScanViewModel(
            scanCoordinator = coordinator,
            scanStateRepository = scans,
            permissionPort = permission,
            preferences = preferences,
            completionHandler = completion,
            scanWorkScheduler = scheduler,
        )
    }

    private fun completedState(period: AnalysisPeriod): ScanState = ScanState(
        id = ScanId.of("scan-1"),
        period = period,
        lastProcessedMessageId = "9",
        parserVersion = ParserVersion.of("v1"),
        status = ScanStatus.COMPLETED,
        processedCount = 9,
        acceptedCount = 4,
        startedAt = EpochMillis.of(1L),
        completedAt = EpochMillis.of(2L),
        updatedAt = EpochMillis.of(2L),
    )
}
