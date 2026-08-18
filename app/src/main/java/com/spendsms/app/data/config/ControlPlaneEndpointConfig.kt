package com.spendsms.app.data.config

import com.spendsms.app.BuildConfig
import com.spendsms.app.application.port.config.ControlPlaneEndpoints
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects whether Phase-0 control-plane endpoints are configured for real use.
 *
 * Placeholder BuildConfig URLs (for example `*.example.invalid`) keep the app in
 * local-only mode without network/AWS dependencies at runtime.
 */
@Singleton
class ControlPlaneEndpointConfig @Inject constructor() : ControlPlaneEndpoints {

    val apiBaseUrl: String = BuildConfig.API_BASE_URL
    val parserCdnBaseUrl: String = BuildConfig.PARSER_CDN_BASE_URL
    val parserManifestUrl: String = BuildConfig.PARSER_MANIFEST_URL

    override val isApiConfigured: Boolean
        get() = isConfiguredEndpoint(apiBaseUrl)

    override val isParserCdnConfigured: Boolean
        get() = isConfiguredEndpoint(parserCdnBaseUrl) && isConfiguredEndpoint(parserManifestUrl)

    override val isLocalOnlyMode: Boolean
        get() = !isApiConfigured && !isParserCdnConfigured

    companion object {
        private val PLACEHOLDER_MARKERS = listOf(
            "example.invalid",
            "REPLACE_",
            "placeholder",
        )

        private val PLACEHOLDER_HOSTS = setOf(
            "example.invalid",
            "example.com",
            "localhost",
            "127.0.0.1",
        )

        fun isConfiguredEndpoint(rawUrl: String): Boolean {
            val url = rawUrl.trim()
            if (url.isBlank()) return false
            if (PLACEHOLDER_MARKERS.any { url.contains(it, ignoreCase = true) }) {
                return false
            }
            return try {
                val host = URI(url).host?.lowercase()?.takeIf { it.isNotBlank() }
                    ?: return false
                host !in PLACEHOLDER_HOSTS
            } catch (_: Exception) {
                false
            }
        }
    }
}
