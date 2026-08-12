package com.spendsms.app.application.port.config

/**
 * Non-sensitive Phase-0 feature configuration (Step-2 / Step-3 Remote Config).
 *
 * Must not enable new sensitive permissions or financial-data transmission.
 */
data class AppRemoteConfig(
    val controlledImportEnabled: Boolean,
    val subscriptionDetectionEnabled: Boolean,
    val newParserVersionEnabled: Boolean,
    val premiumExperimentEnabled: Boolean,
    val supportSubmissionEnabled: Boolean,
    val minimumSupportedAppVersion: String,
    val configVersion: String? = null,
) {
    init {
        require(minimumSupportedAppVersion.isNotBlank()) {
            "minimumSupportedAppVersion must not be blank"
        }
    }

    companion object {
        fun bundledDefaults(): AppRemoteConfig = AppRemoteConfig(
            controlledImportEnabled = true,
            subscriptionDetectionEnabled = true,
            newParserVersionEnabled = true,
            premiumExperimentEnabled = false,
            supportSubmissionEnabled = false,
            minimumSupportedAppVersion = "0.1.0",
            configVersion = "bundled",
        )
    }
}

/**
 * Cached remote / bundled configuration provider.
 *
 * Priority on failure: last known valid config, then bundled defaults.
 */
interface RemoteConfigPort {

    suspend fun getConfig(): AppRemoteConfig
}
