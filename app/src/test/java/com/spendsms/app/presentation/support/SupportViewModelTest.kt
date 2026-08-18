package com.spendsms.app.presentation.support

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.controlplane.ControlPlaneStatus
import com.spendsms.app.application.controlplane.SupportSubmissionUiResult
import io.mockk.coEvery
import io.mockk.coVerify
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
class SupportViewModelTest {

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
    fun refresh_whenSupportDisabled_showsUnavailableWithoutRetry() = runTest {
        val controlPlane = mockk<ControlPlaneCoordinator>()
        coEvery { controlPlane.currentStatus() } returns ControlPlaneStatus.localOnly()
        val vm = SupportViewModel(controlPlane)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(SupportUiState.Unavailable::class.java)
    }

    @Test
    fun submit_whenDisabled_doesNotCallCoordinator() = runTest {
        val controlPlane = mockk<ControlPlaneCoordinator>()
        coEvery { controlPlane.currentStatus() } returns ControlPlaneStatus.localOnly()
        val vm = SupportViewModel(controlPlane)
        dispatcher.scheduler.advanceUntilIdle()
        vm.submitSampleRedactedTemplate()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(SupportUiState.Unavailable::class.java)
        coVerify(exactly = 0) { controlPlane.submitSupport(any()) }
    }

    @Test
    fun submit_whenEnabledButRejected_showsServiceUnavailable() = runTest {
        val controlPlane = mockk<ControlPlaneCoordinator>()
        coEvery { controlPlane.currentStatus() } returns ControlPlaneStatus.localOnly().copy(
            supportSubmissionEnabled = true,
            isLocalOnlyMode = false,
        )
        coEvery { controlPlane.submitSupport(any()) } returns SupportSubmissionUiResult.Rejected("s1")
        val vm = SupportViewModel(controlPlane)
        dispatcher.scheduler.advanceUntilIdle()
        vm.submitSampleRedactedTemplate()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(SupportUiState.ServiceUnavailable)
    }
}
