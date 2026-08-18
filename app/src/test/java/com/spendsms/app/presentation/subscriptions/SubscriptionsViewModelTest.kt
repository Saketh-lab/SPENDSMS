package com.spendsms.app.presentation.subscriptions

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.SubscriptionRepository
import com.spendsms.app.application.subscriptions.SubscriptionDetectionService
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.SubscriptionFrequency
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.subscriptions.Subscription
import com.spendsms.app.presentation.common.AsyncUiState
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
class SubscriptionsViewModelTest {

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
    fun confirm_invalidatesAndRecomputesDashboard() = runTest {
        val repo = mockk<SubscriptionRepository>()
        val detection = mockk<SubscriptionDetectionService>(relaxed = true)
        val dashboard = mockk<DashboardService>(relaxed = true)
        val preferences = mockk<UserPreferencesStore>()
        every { preferences.lastAnalysisPeriod } returns flowOf(period)
        val sub = sampleSub()
        coEvery { repo.listAll() } returns listOf(sub) andThen listOf(
            sub.copy(status = SubscriptionStatus.CONFIRMED),
        )
        coEvery { detection.updateStatus(SubscriptionId.of("s1"), SubscriptionStatus.CONFIRMED) } returns
            sub.copy(status = SubscriptionStatus.CONFIRMED)

        val vm = SubscriptionsViewModel(repo, detection, dashboard, preferences)
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirm("s1")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { dashboard.invalidateCache() }
        coVerify { dashboard.recomputeAndCache(period) }
        val ready = vm.state.value as AsyncUiState.Ready
        assertThat(ready.value.confirmed).hasSize(1)
    }

    private fun sampleSub() = Subscription(
        id = SubscriptionId.of("s1"),
        merchantKey = MerchantKey.of("music"),
        merchantDisplayName = "Music",
        frequency = SubscriptionFrequency.MONTHLY,
        estimatedAmount = Money.ofMinorUnits(100L, CurrencyCode.INR),
        currency = CurrencyCode.INR,
        lastPaymentDate = EpochMillis.of(1L),
        estimatedNextDate = EpochMillis.of(2L),
        confidence = Confidence.of(0.8),
        status = SubscriptionStatus.SUSPECTED,
        evidenceTransactionIds = emptyList(),
        createdAt = EpochMillis.of(1L),
        updatedAt = EpochMillis.of(1L),
    )
}
