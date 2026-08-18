package com.spendsms.app.presentation.settings

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.controlplane.ControlPlaneStatus
import com.spendsms.app.application.port.sms.SmsPermissionPort
import io.mockk.coEvery
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
class SettingsViewModelTest {

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
    fun refresh_exposesLocalOnlyControlPlaneStatus() = runTest {
        val controlPlane = mockk<ControlPlaneCoordinator>()
        coEvery { controlPlane.currentStatus() } returns ControlPlaneStatus.localOnly()
        val vm = SettingsViewModel(
            permissionPort = SmsPermissionPort { true },
            controlPlane = controlPlane,
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.ui.value.controlPlane.isLocalOnlyMode).isTrue()
        assertThat(vm.ui.value.controlPlane.parserUpdatesEnabled).isFalse()
        assertThat(vm.ui.value.controlPlane.supportSubmissionEnabled).isFalse()
    }
}
