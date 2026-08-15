package com.spendsms.app.data.parser.update

import com.spendsms.app.application.port.parser.ManifestFetchResult
import com.spendsms.app.application.port.parser.PackageDownloadResult
import com.spendsms.app.application.port.parser.ParserUpdateRemotePort
import com.spendsms.app.data.parser.AppVersionProvider
import com.spendsms.app.data.parser.ParserRulesValidator
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * CloudFront/S3 static fetch for parser manifest + packages.
 *
 * Safe headers only: Accept, If-None-Match, User-Agent, X-App-Version, X-Platform.
 * Never attaches SMS or financial payloads.
 */
@Singleton
class OkHttpParserUpdateRemote @Inject constructor(
    okHttpClient: OkHttpClient,
    private val manifestCodec: ParserManifestCodec,
    private val allowList: ParserCdnAllowList,
    private val appVersionProvider: AppVersionProvider,
    @Named(PARSER_MANIFEST_URL) private val manifestUrl: String,
) : ParserUpdateRemotePort {

    private val client: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchManifest(ifNoneMatch: String?): ManifestFetchResult {
        if (!allowList.isAllowed(manifestUrl)) {
            return ManifestFetchResult.Failed("Manifest URL host is not allow-listed")
        }
        val request = baseRequest(manifestUrl)
            .get()
            .apply {
                if (!ifNoneMatch.isNullOrBlank()) {
                    header(HEADER_IF_NONE_MATCH, ifNoneMatch)
                }
            }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val etag = response.header(HEADER_ETAG)
                when (response.code) {
                    304 -> ManifestFetchResult.NotModified(etag = etag ?: ifNoneMatch)
                    in 200..299 -> {
                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) {
                            ManifestFetchResult.Failed("Empty parser manifest body")
                        } else {
                            try {
                                ManifestFetchResult.Available(
                                    manifest = manifestCodec.decode(body),
                                    etag = etag,
                                )
                            } catch (e: ParserManifestCodecException) {
                                ManifestFetchResult.Failed(e.message ?: "Invalid manifest")
                            }
                        }
                    }
                    else -> ManifestFetchResult.Failed(
                        "Manifest HTTP ${response.code}",
                    )
                }
            }
        } catch (_: IOException) {
            ManifestFetchResult.Offline
        }
    }

    override suspend fun downloadPackage(packageUrl: String): PackageDownloadResult {
        if (!allowList.isAllowed(packageUrl)) {
            return PackageDownloadResult.Failed("Package URL host is not allow-listed")
        }
        val request = baseRequest(packageUrl).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code !in 200..299) {
                    return PackageDownloadResult.Failed("Package HTTP ${response.code}")
                }
                val bytes = response.body?.bytes()
                    ?: return PackageDownloadResult.Failed("Empty parser package body")
                if (bytes.isEmpty()) {
                    return PackageDownloadResult.Failed("Empty parser package body")
                }
                PackageDownloadResult.Downloaded(
                    bodyUtf8 = bytes.toString(Charsets.UTF_8),
                    sha256Hex = ParserRulesValidator.sha256Hex(bytes),
                )
            }
        } catch (_: IOException) {
            PackageDownloadResult.Offline
        }
    }

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header(HEADER_ACCEPT, "application/json")
            .header(HEADER_USER_AGENT, "SpendSMS-Phase0")
            .header(HEADER_APP_VERSION, appVersionProvider.appVersionName())
            .header(HEADER_PLATFORM, "android")

    companion object {
        const val PARSER_MANIFEST_URL = "parser_manifest_url"

        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_IF_NONE_MATCH = "If-None-Match"
        private const val HEADER_ETAG = "ETag"
        private const val HEADER_APP_VERSION = "X-App-Version"
        private const val HEADER_PLATFORM = "X-Platform"
    }
}
