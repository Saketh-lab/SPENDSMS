package com.spendsms.app.data.config

import com.spendsms.app.application.port.config.AppRemoteConfig
import com.spendsms.app.application.port.config.ControlPlaneEndpoints
import com.spendsms.app.application.port.config.RemoteConfigPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-0 offline-capable remote config.
 *
 * Returns bundled defaults locally. When CDN/API endpoints are configured in
 * BuildConfig, feature flags reflect which remote services may be used; actual
 * CDN-fetched config can replace this adapter later without changing callers.
 */
@Singleton
class BundledRemoteConfigPort @Inject constructor(
    private val endpoints: ControlPlaneEndpoints,
) : RemoteConfigPort {

    override suspend fun getConfig(): AppRemoteConfig {
        val base = AppRemoteConfig.bundledDefaults()
        return if (endpoints.isLocalOnlyMode) {
            AppRemoteConfig.localOnlyDefaults()
        } else {
            base.copy(
                newParserVersionEnabled = endpoints.isParserCdnConfigured,
                supportSubmissionEnabled = endpoints.isApiConfigured,
                configVersion = when {
                    endpoints.isParserCdnConfigured && endpoints.isApiConfigured -> "bundled-partial"
                    endpoints.isParserCdnConfigured -> "bundled-parser-cdn"
                    endpoints.isApiConfigured -> "bundled-api"
                    else -> "bundled"
                },
            )
        }
    }
}
