package com.spendsms.app.data.parser.update

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restricts parser-update HTTPS calls to approved CDN hosts (Phase-0 CloudFront).
 */
@Singleton
class ParserCdnAllowList @Inject constructor(
    private val allowedHosts: Set<String>,
) {
    fun isAllowed(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        return isAllowed(httpUrl)
    }

    fun isAllowed(url: HttpUrl): Boolean {
        if (!url.isHttps) return false
        return url.host.lowercase() in allowedHosts
    }

    companion object {
        fun hostsFromUrls(vararg urls: String): Set<String> =
            urls.mapNotNull { raw ->
                runCatching { URI(raw).host?.lowercase()?.takeIf { it.isNotBlank() } }
                    .getOrNull()
            }.toSet()
    }
}
