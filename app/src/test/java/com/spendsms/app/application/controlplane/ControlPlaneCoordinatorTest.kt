package com.spendsms.app.application.controlplane

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserBundleFailureReason
import com.spendsms.app.application.parser.ParserUpdateOutcome
import com.spendsms.app.application.parser.ParserUpdateService
import com.spendsms.app.application.parser.ParserUpdateSkipReason
import com.spendsms.app.application.port.config.AppRemoteConfig
import com.spendsms.app.application.port.config.ControlPlaneEndpoints
import com.spendsms.app.application.port.config.RemoteConfigPort
import com.spendsms.app.application.port.support.RedactedUnsupportedFormatSubmission
import com.spendsms.app.application.port.support.SupportSubmissionPort
import com.spendsms.app.application.port.support.SupportSubmissionResult
import com.spendsms.app.application.port.support.UnsupportedFormatReason
import com.spendsms.app.application.port.telemetry.TelemetryEvent
import com.spendsms.app.application.port.telemetry.TelemetryEventType
import com.spendsms.app.application.port.telemetry.TelemetryPort
import com.spendsms.app.data.telemetry.SilentTelemetryRecorder
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.data.parser.ParserBundleTestFixtures
import com.spendsms.app.domain.model.ParserMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ControlPlaneCoordinatorTest {

    private val bundledRules = ParserBundleTestFixtures.sampleRules(
        parserVersion = "bundled-2026.08.12.0",
        rulesVersion = "bundled-rules-1",
    )

    private val activated by lazy {
        ParserBundleActivationResult.Activated(
            metadata = ParserMetadata(
                parserVersion = bundledRules.parserVersion,
                rulesVersion = bundledRules.rulesVersion,
                schemaVersion = bundledRules.schemaVersion,
                checksum = "abc",
                installedAt = EpochMillis.of(1L),
                activatedAt = EpochMillis.of(1L),
                status = com.spendsms.app.domain.model.ParserBundleStatus.ACTIVE,
            ),
            rules = bundledRules,
            usedBundledFallback = true,
        )
    }

    @Test
    fun currentStatus_localOnly_disablesRemoteFeatures() = runBlocking {
        val coordinator = coordinator(
            endpoints = FakeEndpoints(localOnly = true),
            remoteConfig = AppRemoteConfig.localOnlyDefaults(),
        )
        val status = coordinator.currentStatus()
        assertThat(status.isLocalOnlyMode).isTrue()
        assertThat(status.parserUpdatesEnabled).isFalse()
        assertThat(status.supportSubmissionEnabled).isFalse()
        assertThat(status.telemetryEnabled).isFalse()
        assertThat(status.parserSourceLabel).contains("offline")
    }

    @Test
    fun bootstrap_success_doesNotThrow_andSkipsRemoteUpdateWhenLocalOnly() = runBlocking {
        val parser = mockk<ParserUpdateService>()
        coEvery { parser.ensureBundledParserReady() } returns activated
        val coordinator = coordinator(
            parser = parser,
            endpoints = FakeEndpoints(localOnly = true),
        )
        val result = coordinator.bootstrap()
        assertThat(result).isInstanceOf(ControlPlaneBootstrapResult.Ready::class.java)
        coVerify(exactly = 0) { parser.checkAndApplyUpdate(any()) }
    }

    @Test
    fun bootstrap_parserFailure_returnsDegradedWithoutThrowing() = runBlocking {
        val parser = mockk<ParserUpdateService>()
        coEvery { parser.ensureBundledParserReady() } returns ParserBundleActivationResult.Failed(
            reason = ParserBundleFailureReason.BUNDLED_FALLBACK_UNAVAILABLE,
            detail = "missing asset",
        )
        val coordinator = coordinator(parser = parser)
        val result = coordinator.bootstrap()
        assertThat(result).isInstanceOf(ControlPlaneBootstrapResult.ParserDegraded::class.java)
    }

    @Test
    fun bootstrap_whenCdnConfigured_checksRemoteUpdateWithoutThrowing() = runBlocking {
        val parser = mockk<ParserUpdateService>()
        coEvery { parser.ensureBundledParserReady() } returns activated
        coEvery { parser.checkAndApplyUpdate(force = false) } returns ParserUpdateOutcome.Skipped(
            reason = ParserUpdateSkipReason.FEATURE_DISABLED,
            detail = "disabled",
            activeVersion = ParserVersion.of("bundled-2026.08.12.0"),
        )
        val coordinator = coordinator(
            parser = parser,
            endpoints = FakeEndpoints(localOnly = false, cdn = true, api = true),
            remoteConfig = AppRemoteConfig.bundledDefaults(),
        )
        coordinator.bootstrap()
        coVerify(exactly = 1) { parser.checkAndApplyUpdate(force = false) }
    }

    @Test
    fun submitSupport_whenDisabled_returnsFeatureDisabledWithoutCallingPort() = runBlocking {
        val support = mockk<SupportSubmissionPort>()
        val coordinator = coordinator(
            support = support,
            endpoints = FakeEndpoints(localOnly = true),
            remoteConfig = AppRemoteConfig.localOnlyDefaults(),
        )
        val result = coordinator.submitSupport(sampleSubmission())
        assertThat(result).isEqualTo(SupportSubmissionUiResult.FeatureDisabled)
        coVerify(exactly = 0) { support.submit(any()) }
    }

    @Test
    fun submitSupport_whenEnabledButPortRejects_returnsRejected() = runBlocking {
        val support = mockk<SupportSubmissionPort>()
        coEvery { support.submit(any()) } returns SupportSubmissionResult(
            submissionId = "s1",
            accepted = false,
            deletionScheduledAt = null,
        )
        val coordinator = coordinator(
            support = support,
            endpoints = FakeEndpoints(localOnly = false, cdn = false, api = true),
            remoteConfig = AppRemoteConfig.bundledDefaults().copy(supportSubmissionEnabled = true),
        )
        val result = coordinator.submitSupport(sampleSubmission())
        assertThat(result).isEqualTo(SupportSubmissionUiResult.Rejected("s1"))
    }

    @Test
    fun telemetryRecorder_swallowsEnqueueFailures() = runBlocking {
        val telemetry = mockk<TelemetryPort>()
        coEvery { telemetry.enqueue(any()) } throws RuntimeException("network down")
        val recorder = SilentTelemetryRecorder(telemetry)
        recorder.record(
            listOf(
                TelemetryEvent(
                    eventId = "e1",
                    occurredAt = EpochMillis.of(1L),
                    eventType = TelemetryEventType.SCAN_STARTED,
                ),
            ),
        )
    }

    @Test
    fun recordScanStarted_enqueuesAllowListedEvent() = runBlocking {
        val telemetry = mockk<TelemetryPort>(relaxed = true)
        val events = slot<List<TelemetryEvent>>()
        coEvery { telemetry.enqueue(capture(events)) } returns Unit
        val coordinator = coordinator(telemetry = SilentTelemetryRecorder(telemetry))
        coordinator.recordScanStarted()
        assertThat(events.captured.single().eventType).isEqualTo(TelemetryEventType.SCAN_STARTED)
    }

    private fun coordinator(
        parser: ParserUpdateService = mockk(relaxed = true) {
            coEvery { ensureBundledParserReady() } returns activated
        },
        support: SupportSubmissionPort = mockk(relaxed = true),
        telemetry: SilentTelemetryRecorder = SilentTelemetryRecorder(mockk(relaxed = true)),
        endpoints: ControlPlaneEndpoints = FakeEndpoints(localOnly = true),
        remoteConfig: AppRemoteConfig = AppRemoteConfig.localOnlyDefaults(),
    ): ControlPlaneCoordinator = ControlPlaneCoordinator(
        endpoints = endpoints,
        remoteConfig = MutableRemoteConfig(remoteConfig),
        parserUpdateService = parser,
        supportSubmissionPort = support,
        telemetry = telemetry,
    )

    private fun sampleSubmission() = RedactedUnsupportedFormatSubmission(
        submissionId = "s1",
        idempotencyKey = "k1",
        consentedAt = EpochMillis.of(1L),
        previewConfirmed = true,
        redactionVersion = "1",
        reason = UnsupportedFormatReason.NO_RULE_MATCH,
        redactedTemplate = "Debited [AMOUNT]",
        parserVersion = null,
    )
}

private class FakeEndpoints(
    private val localOnly: Boolean,
    private val cdn: Boolean = false,
    private val api: Boolean = false,
) : ControlPlaneEndpoints {
    override val isApiConfigured: Boolean get() = api
    override val isParserCdnConfigured: Boolean get() = cdn
    override val isLocalOnlyMode: Boolean get() = localOnly
}

private class MutableRemoteConfig(
    private var config: AppRemoteConfig,
) : RemoteConfigPort {
    override suspend fun getConfig(): AppRemoteConfig = config
}
