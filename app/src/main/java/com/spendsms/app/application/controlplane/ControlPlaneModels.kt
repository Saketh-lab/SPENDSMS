package com.spendsms.app.application.controlplane

/**
 * UI/application snapshot of Phase-0 control-plane availability.
 *
 * Derived from [com.spendsms.app.application.port.config.RemoteConfigPort] and
 * [com.spendsms.app.application.port.config.ControlPlaneEndpoints].
 */
data class ControlPlaneStatus(
    val isLocalOnlyMode: Boolean,
    val parserUpdatesEnabled: Boolean,
    val supportSubmissionEnabled: Boolean,
    val telemetryEnabled: Boolean,
    val parserSourceLabel: String,
    val configVersion: String,
) {
    val cloudSyncEnabled: Boolean
        get() = !isLocalOnlyMode

    companion object {
        fun localOnly(): ControlPlaneStatus = ControlPlaneStatus(
            isLocalOnlyMode = true,
            parserUpdatesEnabled = false,
            supportSubmissionEnabled = false,
            telemetryEnabled = false,
            parserSourceLabel = "Bundled (offline)",
            configVersion = "local",
        )
    }
}

sealed interface ControlPlaneBootstrapResult {
    data class Ready(
        val activeParserVersion: String?,
    ) : ControlPlaneBootstrapResult

    /** Bundled parser could not be activated; app may still route but scans may fail locally. */
    data class ParserDegraded(
        val detail: String,
    ) : ControlPlaneBootstrapResult
}

sealed interface SupportSubmissionUiResult {
    data object FeatureDisabled : SupportSubmissionUiResult
    data object Unavailable : SupportSubmissionUiResult
    data class Accepted(
        val submissionId: String,
    ) : SupportSubmissionUiResult
    data class Rejected(
        val submissionId: String,
    ) : SupportSubmissionUiResult
}
