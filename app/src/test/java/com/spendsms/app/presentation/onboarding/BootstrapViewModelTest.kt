package com.spendsms.app.presentation.onboarding

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.controlplane.ControlPlaneBootstrapResult
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.domain.model.ScanStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class BootstrapViewModelTest {

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
    fun notOnboarded_routesToOnboarding() = runTest {
        val preferences = mockk<UserPreferencesStore>()
        every { preferences.onboardingCompleted } returns flowOf(false)
        val vm = BootstrapViewModel(
            preferences = preferences,
            scanStateRepository = mockk(relaxed = true),
            permissionPort = SmsPermissionPort { false },
            controlPlane = mockControlPlane(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.destination.value).isEqualTo(BootstrapDestination.Onboarding)
    }

    @Test
    fun onboardedWithCompletedScan_routesToDashboard() = runTest {
        val preferences = mockk<UserPreferencesStore>()
        every { preferences.onboardingCompleted } returns flowOf(true)
        val scans = mockk<ScanStateRepository>()
        coEvery { scans.findLatestCompleted() } returns ScanState(
            id = ScanId.of("s1"),
            period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L)),
            lastProcessedMessageId = "1",
            parserVersion = ParserVersion.of("v1"),
            status = ScanStatus.COMPLETED,
            processedCount = 1,
            acceptedCount = 1,
            startedAt = EpochMillis.of(1L),
            completedAt = EpochMillis.of(2L),
            updatedAt = EpochMillis.of(2L),
        )
        coEvery { scans.findResumable() } returns null
        val vm = BootstrapViewModel(preferences, scans, SmsPermissionPort { true }, mockControlPlane())
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.destination.value).isEqualTo(BootstrapDestination.Dashboard)
    }

    @Test
    fun onboardedWithResumableAndCompleted_routesToScan() = runTest {
        val preferences = mockk<UserPreferencesStore>()
        every { preferences.onboardingCompleted } returns flowOf(true)
        val scans = mockk<ScanStateRepository>()
        val period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L))
        coEvery { scans.findLatestCompleted() } returns ScanState(
            id = ScanId.of("done"),
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
        coEvery { scans.findResumable() } returns ScanState(
            id = ScanId.of("resume"),
            period = period,
            lastProcessedMessageId = "3",
            parserVersion = ParserVersion.of("v1"),
            status = ScanStatus.RUNNING,
            processedCount = 3,
            acceptedCount = 2,
            startedAt = EpochMillis.of(3L),
            completedAt = null,
            updatedAt = EpochMillis.of(4L),
        )
        val vm = BootstrapViewModel(preferences, scans, SmsPermissionPort { true }, mockControlPlane())
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.destination.value).isEqualTo(BootstrapDestination.ScanPeriod)
    }

    @Test
    fun bootstrap_runsInBackground_andDoesNotBlockRouting() = runTest {
        val controlPlane = mockk<ControlPlaneCoordinator>()
        coEvery { controlPlane.bootstrap() } returns ControlPlaneBootstrapResult.ParserDegraded("missing")
        val preferences = mockk<UserPreferencesStore>()
        every { preferences.onboardingCompleted } returns flowOf(false)
        val vm = BootstrapViewModel(
            preferences = preferences,
            scanStateRepository = mockk(relaxed = true),
            permissionPort = SmsPermissionPort { false },
            controlPlane = controlPlane,
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.destination.value).isEqualTo(BootstrapDestination.Onboarding)
        coVerify(exactly = 1) { controlPlane.bootstrap() }
    }

    private fun mockControlPlane(): ControlPlaneCoordinator {
        val controlPlane = mockk<ControlPlaneCoordinator>()
        coEvery { controlPlane.bootstrap() } returns ControlPlaneBootstrapResult.Ready("bundled-2026.08.12.0")
        return controlPlane
    }
}
