package com.spendsms.app.application.controlplane

import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserUpdateOutcome
import com.spendsms.app.application.parser.ParserUpdateService
import com.spendsms.app.application.port.config.ControlPlaneEndpoints
import com.spendsms.app.application.port.config.RemoteConfigPort
import com.spendsms.app.application.port.support.RedactedUnsupportedFormatSubmission
import com.spendsms.app.application.port.support.SupportSubmissionPort
import com.spendsms.app.application.port.telemetry.TelemetryEvent
import com.spendsms.app.application.port.telemetry.TelemetryEventType
import com.spendsms.app.data.telemetry.SilentTelemetryRecorder
import com.spendsms.app.domain.model.EpochMillis
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application facade for Phase-0 control-plane ports.
 *
 * Keeps domain/application callers free of AWS/CDN details while local-only adapters
 * are active. Network implementations can replace data-layer bindings later.
 */
@Singleton
class ControlPlaneCoordinator @Inject constructor(
    private val endpoints: ControlPlaneEndpoints,
    private val remoteConfig: RemoteConfigPort,
    private val parserUpdateService: ParserUpdateService,
    private val supportSubmissionPort: SupportSubmissionPort,
    private val telemetry: SilentTelemetryRecorder,
) {

    suspend fun currentStatus(): ControlPlaneStatus {
        val config = remoteConfig.getConfig()
        return ControlPlaneStatus(
            isLocalOnlyMode = endpoints.isLocalOnlyMode,
            parserUpdatesEnabled = config.newParserVersionEnabled && endpoints.isParserCdnConfigured,
            supportSubmissionEnabled = config.supportSubmissionEnabled && endpoints.isApiConfigured,
            telemetryEnabled = endpoints.isApiConfigured,
            parserSourceLabel = if (endpoints.isParserCdnConfigured) {
                "Bundled (remote updates available)"
            } else {
                "Bundled (offline)"
            },
            configVersion = config.configVersion ?: "bundled",
        )
    }

    /**
     * Non-blocking startup bootstrap: activate bundled parser, optionally check CDN when configured.
     * Never throws; failures are degraded, not fatal to navigation.
     */
    suspend fun bootstrap(): ControlPlaneBootstrapResult {
        val activation = runCatching { parserUpdateService.ensureBundledParserReady() }
            .getOrElse { error ->
                recordAppError("PARSER_BOOTSTRAP_EXCEPTION")
                return ControlPlaneBootstrapResult.ParserDegraded(
                    detail = error.message ?: "Parser bootstrap failed",
                )
            }

        return when (activation) {
            is ParserBundleActivationResult.Activated -> {
                maybeCheckRemoteParserUpdate()
                ControlPlaneBootstrapResult.Ready(
                    activeParserVersion = activation.metadata.parserVersion.value,
                )
            }

            is ParserBundleActivationResult.Failed -> {
                recordAppError("PARSER_BOOTSTRAP_FAILED")
                ControlPlaneBootstrapResult.ParserDegraded(detail = activation.detail)
            }
        }
    }

    suspend fun ensureParserReadyForScan(): ParserBundleActivationResult =
        runCatching { parserUpdateService.ensureBundledParserReady() }
            .getOrElse { error ->
                ParserBundleActivationResult.Failed(
                    reason = com.spendsms.app.application.parser.ParserBundleFailureReason.BUNDLED_FALLBACK_UNAVAILABLE,
                    detail = error.message ?: "Parser bootstrap failed",
                )
            }

    suspend fun submitSupport(
        submission: RedactedUnsupportedFormatSubmission,
    ): SupportSubmissionUiResult {
        val status = currentStatus()
        if (!status.supportSubmissionEnabled) {
            return SupportSubmissionUiResult.FeatureDisabled
        }
        return runCatching { supportSubmissionPort.submit(submission) }
            .fold(
                onSuccess = { result ->
                    if (result.accepted) {
                        SupportSubmissionUiResult.Accepted(result.submissionId)
                    } else {
                        SupportSubmissionUiResult.Rejected(result.submissionId)
                    }
                },
                onFailure = { SupportSubmissionUiResult.Unavailable },
            )
    }

    suspend fun recordScanStarted() {
        recordTelemetry(TelemetryEventType.SCAN_STARTED)
    }

    suspend fun recordScanCompleted() {
        recordTelemetry(TelemetryEventType.SCAN_COMPLETED)
    }

    suspend fun recordScanFailed(errorCode: String = "SCAN_FAILED") {
        recordTelemetry(TelemetryEventType.SCAN_FAILED, errorCode = errorCode)
    }

    private suspend fun maybeCheckRemoteParserUpdate() {
        if (endpoints.isLocalOnlyMode) return
        val config = remoteConfig.getConfig()
        if (!config.newParserVersionEnabled || !endpoints.isParserCdnConfigured) return

        runCatching { parserUpdateService.checkAndApplyUpdate(force = false) }
            .onSuccess { outcome ->
                when (outcome) {
                    is ParserUpdateOutcome.Updated ->
                        recordTelemetry(TelemetryEventType.PARSER_UPDATE_ACTIVATED)
                    is ParserUpdateOutcome.Failed,
                    is ParserUpdateOutcome.Rejected,
                    -> recordTelemetry(
                        TelemetryEventType.PARSER_UPDATE_FAILED,
                        errorCode = "PARSER_UPDATE_FAILED",
                    )
                    else -> recordTelemetry(TelemetryEventType.PARSER_UPDATE_CHECKED)
                }
            }
            .onFailure { recordAppError("PARSER_UPDATE_CHECK_EXCEPTION") }
    }

    private suspend fun recordTelemetry(
        type: TelemetryEventType,
        errorCode: String? = null,
    ) {
        telemetry.record(
            listOf(
                TelemetryEvent(
                    eventId = UUID.randomUUID().toString(),
                    occurredAt = EpochMillis.of(System.currentTimeMillis()),
                    eventType = type,
                    errorCode = errorCode,
                ),
            ),
        )
    }

    private suspend fun recordAppError(code: String) {
        recordTelemetry(TelemetryEventType.APP_ERROR, errorCode = code)
    }
}
