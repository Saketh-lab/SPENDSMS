package com.spendsms.app.data.config

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.port.config.AppRemoteConfig
import com.spendsms.app.application.port.support.RedactedUnsupportedFormatSubmission
import com.spendsms.app.application.port.support.UnsupportedFormatReason
import com.spendsms.app.application.port.telemetry.TelemetryEvent
import com.spendsms.app.application.port.telemetry.TelemetryEventType
import com.spendsms.app.data.support.UnavailableSupportSubmissionPort
import com.spendsms.app.data.telemetry.NoOpTelemetryPort
import com.spendsms.app.domain.model.EpochMillis
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Proves Phase-0 works with placeholder BuildConfig URLs and no AWS/CDN/API wiring.
 */
class LocalOnlyModeTest {

    @Test
    fun placeholderUrls_areNotConfiguredEndpoints() {
        assertThat(ControlPlaneEndpointConfig.isConfiguredEndpoint("https://api.example.invalid/"))
            .isFalse()
        assertThat(ControlPlaneEndpointConfig.isConfiguredEndpoint("https://cdn.example.invalid/"))
            .isFalse()
        assertThat(
            ControlPlaneEndpointConfig.isConfiguredEndpoint(
                "https://cdn.example.invalid/parser/manifest.json",
            ),
        ).isFalse()
        assertThat(ControlPlaneEndpointConfig.isConfiguredEndpoint(""))
            .isFalse()
    }

    @Test
    fun realUrls_areConfiguredEndpoints() {
        assertThat(
            ControlPlaneEndpointConfig.isConfiguredEndpoint("https://d123.cloudfront.net/"),
        ).isTrue()
        assertThat(
            ControlPlaneEndpointConfig.isConfiguredEndpoint(
                "https://abc123.execute-api.ap-south-1.amazonaws.com/",
            ),
        ).isTrue()
    }

    @Test
    fun bundledRemoteConfig_returnsLocalDefaultsForPlaceholders() = runBlocking {
        val endpoints = object : com.spendsms.app.application.port.config.ControlPlaneEndpoints {
            override val isApiConfigured = false
            override val isParserCdnConfigured = false
            override val isLocalOnlyMode = true
        }
        val config = BundledRemoteConfigPort(endpoints).getConfig()
        assertThat(config.configVersion).isEqualTo("local")
        assertThat(config.newParserVersionEnabled).isFalse()
        assertThat(config.supportSubmissionEnabled).isFalse()
        assertThat(config.controlledImportEnabled).isTrue()
        assertThat(config.subscriptionDetectionEnabled).isTrue()
    }

    @Test
    fun localOnlyDefaults_matchBundledFeatureFlagsExceptRemoteServices() {
        val local = AppRemoteConfig.localOnlyDefaults()
        assertThat(local.newParserVersionEnabled).isFalse()
        assertThat(local.supportSubmissionEnabled).isFalse()
        assertThat(local.controlledImportEnabled).isTrue()
        assertThat(local.subscriptionDetectionEnabled).isTrue()
    }

    @Test
    fun noOpTelemetryPort_acceptsEventsWithoutSideEffects() = runBlocking {
        val port = NoOpTelemetryPort()
        port.enqueue(
            listOf(
                TelemetryEvent(
                    eventId = "e1",
                    occurredAt = EpochMillis.of(1L),
                    eventType = TelemetryEventType.SCAN_COMPLETED,
                ),
            ),
        )
        port.flush()
    }

    @Test
    fun unavailableSupportPort_returnsGracefulRejection() = runBlocking {
        val port = UnavailableSupportSubmissionPort()
        val result = port.submit(
            RedactedUnsupportedFormatSubmission(
                submissionId = "s1",
                idempotencyKey = "k1",
                consentedAt = EpochMillis.of(1L),
                previewConfirmed = true,
                redactionVersion = "1",
                reason = UnsupportedFormatReason.NO_RULE_MATCH,
                redactedTemplate = "Debited [AMOUNT]",
                parserVersion = null,
            ),
        )
        assertThat(result.submissionId).isEqualTo("s1")
        assertThat(result.accepted).isFalse()
        assertThat(result.deletionScheduledAt).isNull()
    }

    @Test
    fun buildConfigPlaceholders_implyLocalOnlyMode() {
        val config = ControlPlaneEndpointConfig()
        assertThat(config.isLocalOnlyMode).isTrue()
        assertThat(config.isApiConfigured).isFalse()
        assertThat(config.isParserCdnConfigured).isFalse()
    }
}
