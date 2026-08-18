package com.spendsms.app.data.config

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.controlplane.ControlPlaneBootstrapResult
import com.spendsms.app.application.controlplane.ControlPlaneCoordinator
import com.spendsms.app.application.parser.ParserUpdateService
import com.spendsms.app.application.port.config.AppRemoteConfig
import com.spendsms.app.application.port.config.RemoteConfigPort
import com.spendsms.app.data.support.UnavailableSupportSubmissionPort
import com.spendsms.app.data.telemetry.NoOpTelemetryPort
import com.spendsms.app.data.telemetry.SilentTelemetryRecorder
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Integration-style checks that local-only adapters compose without network side effects.
 */
class LocalControlPlaneIntegrationTest {

    @Test
    fun localOnlyStack_bootstrapAndStatus_withoutNetworkPorts() = runBlocking {
        val endpoints = ControlPlaneEndpointConfig()
        assertThat(endpoints.isLocalOnlyMode).isTrue()

        val remoteConfig = BundledRemoteConfigPort(endpoints)
        val config = remoteConfig.getConfig()
        assertThat(config.configVersion).isEqualTo("local")
        assertThat(config.newParserVersionEnabled).isFalse()

        val telemetry = NoOpTelemetryPort()
        val recorder = SilentTelemetryRecorder(telemetry)
        val support = UnavailableSupportSubmissionPort()
        val parser = mockk<ParserUpdateService>(relaxed = true)

        val coordinator = ControlPlaneCoordinator(
            endpoints = endpoints,
            remoteConfig = remoteConfig,
            parserUpdateService = parser,
            supportSubmissionPort = support,
            telemetry = recorder,
        )

        val status = coordinator.currentStatus()
        assertThat(status.supportSubmissionEnabled).isFalse()
        assertThat(status.parserUpdatesEnabled).isFalse()

        coordinator.bootstrap()
        coVerify(exactly = 0) { parser.checkAndApplyUpdate(any()) }
    }

    @Test
    fun placeholderBuildConfig_impliesNoRemoteParserChecksInDefaultService() = runBlocking {
        val endpoints = ControlPlaneEndpointConfig()
        val remoteConfig = object : RemoteConfigPort {
            override suspend fun getConfig() = AppRemoteConfig.localOnlyDefaults()
        }
        val parserUpdate = mockk<ParserUpdateService>(relaxed = true)
        val coordinator = ControlPlaneCoordinator(
            endpoints = endpoints,
            remoteConfig = remoteConfig,
            parserUpdateService = parserUpdate,
            supportSubmissionPort = UnavailableSupportSubmissionPort(),
            telemetry = SilentTelemetryRecorder(NoOpTelemetryPort()),
        )
        val result = coordinator.bootstrap()
        assertThat(
            result is ControlPlaneBootstrapResult.Ready || result is ControlPlaneBootstrapResult.ParserDegraded,
        ).isTrue()
    }
}
