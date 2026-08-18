package com.spendsms.app.presentation.settings

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.FinancialDataDeletionPort
import com.spendsms.app.application.port.ParserBundleRepository
import com.spendsms.app.application.port.ScanWorkScheduler
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.model.ParserBundleStatus
import com.spendsms.app.domain.model.EpochMillis
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataDeletionViewModelTest {

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
    fun deleteAnalysedData_usesDeletionPort_andClearsCaches() = runTest {
        val deletion = mockk<FinancialDataDeletionPort>(relaxed = true)
        val dashboard = mockk<DashboardService>(relaxed = true)
        val preferences = mockk<UserPreferencesStore>(relaxed = true)
        val scheduler = mockk<ScanWorkScheduler>(relaxed = true)
        val parsers = mockk<ParserBundleRepository>()
        coEvery { parsers.findActiveMetadata() } returns ParserMetadata(
            parserVersion = ParserVersion.of("bundled-2026.08.12.0"),
            rulesVersion = RulesVersion.of("bundled-rules-1"),
            schemaVersion = 1,
            checksum = "x",
            installedAt = EpochMillis.of(1L),
            activatedAt = EpochMillis.of(1L),
            status = ParserBundleStatus.ACTIVE,
        )
        val vm = DataDeletionViewModel(deletion, dashboard, preferences, scheduler, parsers)

        vm.deleteAnalysedData()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.phase.value).isEqualTo(DeletionPhase.Done)
        assertThat(vm.parserStillPresent.value).isTrue()
        coVerifyOrder {
            scheduler.cancel()
            deletion.deleteAllAnalysedData()
            dashboard.invalidateCache()
            preferences.clearAnalysisPeriod()
        }
    }

    @Test
    fun deleteAnalysedData_failure_surfacesError() = runTest {
        val deletion = mockk<FinancialDataDeletionPort>()
        val dashboard = mockk<DashboardService>(relaxed = true)
        coEvery { deletion.deleteAllAnalysedData() } throws IllegalStateException("disk full")
        val vm = DataDeletionViewModel(
            deletionPort = deletion,
            dashboardService = dashboard,
            preferences = mockk(relaxed = true),
            scanWorkScheduler = mockk(relaxed = true),
            parserBundleRepository = mockk(relaxed = true),
        )

        vm.deleteAnalysedData()
        dispatcher.scheduler.advanceUntilIdle()

        val failed = vm.phase.value as DeletionPhase.Failed
        assertThat(failed.message).contains("disk full")
        coVerify(exactly = 0) { dashboard.invalidateCache() }
    }
}
