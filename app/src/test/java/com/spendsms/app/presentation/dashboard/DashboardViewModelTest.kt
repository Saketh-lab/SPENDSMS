package com.spendsms.app.presentation.dashboard

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.domain.dashboard.SubscriptionTotals
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.Money
import com.spendsms.app.presentation.common.AsyncUiState
import io.mockk.coEvery
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
class DashboardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_withTransactions_emitsReady() = runTest {
        val dashboard = mockk<DashboardService>()
        coEvery { dashboard.getDashboard(period, useCache = true) } returns sampleResult(count = 3)
        val preferences = mockk<UserPreferencesStore>()
        every { preferences.lastAnalysisPeriod } returns flowOf(period)
        val vm = DashboardViewModel(
            dashboardService = dashboard,
            preferences = preferences,
            scanStateRepository = mockk(relaxed = true),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(AsyncUiState.Ready::class.java)
    }

    @Test
    fun refresh_withNoAnalysis_emitsEmpty() = runTest {
        val dashboard = mockk<DashboardService>()
        coEvery { dashboard.getDashboard(period, useCache = true) } returns sampleResult(count = 0)
        val preferences = mockk<UserPreferencesStore>()
        every { preferences.lastAnalysisPeriod } returns flowOf(period)
        val scans = mockk<ScanStateRepository>()
        coEvery { scans.findLatestCompleted() } returns null
        val vm = DashboardViewModel(dashboard, preferences, scans)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(AsyncUiState.Empty::class.java)
    }

    @Test
    fun explicitRefresh_bypassesStaleCache() = runTest {
        val dashboard = mockk<DashboardService>()
        coEvery { dashboard.getDashboard(period, useCache = true) } returns sampleResult(count = 1)
        coEvery { dashboard.getDashboard(period, useCache = false) } returns sampleResult(count = 4)
        val preferences = mockk<UserPreferencesStore>()
        every { preferences.lastAnalysisPeriod } returns flowOf(period)
        val vm = DashboardViewModel(
            dashboardService = dashboard,
            preferences = preferences,
            scanStateRepository = mockk(relaxed = true),
        )
        dispatcher.scheduler.advanceUntilIdle()
        vm.refresh(useCache = false)
        dispatcher.scheduler.advanceUntilIdle()
        val ready = vm.state.value as AsyncUiState.Ready
        assertThat(ready.value.transactionCount).isEqualTo(4)
    }

    private fun sampleResult(count: Int) = DashboardResult(
        period = period,
        grossSpending = Money.zero(CurrencyCode.INR),
        netSpending = Money.zero(CurrencyCode.INR),
        credits = Money.zero(CurrencyCode.INR),
        refunds = Money.zero(CurrencyCode.INR),
        transactionCount = count,
        categoryTotals = emptyList(),
        monthlyTotals = emptyList(),
        merchantTotals = emptyList(),
        subscriptionTotals = SubscriptionTotals(
            estimatedMonthly = Money.zero(),
            estimatedAnnual = Money.zero(),
            activeOrSuspectedCount = 0,
        ),
        recentTransactions = emptyList(),
        suspectedSubscriptions = emptyList(),
        lastAnalysisAt = if (count == 0) null else EpochMillis.of(3L),
    )
}
