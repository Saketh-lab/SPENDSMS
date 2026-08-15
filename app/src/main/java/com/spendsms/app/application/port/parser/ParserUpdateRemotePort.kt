package com.spendsms.app.application.port.parser

import com.spendsms.app.domain.parsing.ParserManifest

/**
 * Fetches parser manifest + packages from allow-listed HTTPS CDN (CloudFront/S3).
 *
 * Requests must never include raw SMS, transactions, merchants, or other financial data.
 */
interface ParserUpdateRemotePort {

    suspend fun fetchManifest(ifNoneMatch: String?): ManifestFetchResult

    suspend fun downloadPackage(packageUrl: String): PackageDownloadResult
}

sealed class ManifestFetchResult {
    data class Available(
        val manifest: ParserManifest,
        val etag: String?,
    ) : ManifestFetchResult()

    data class NotModified(
        val etag: String?,
    ) : ManifestFetchResult()

    data object Offline : ManifestFetchResult()

    data class Failed(
        val detail: String,
    ) : ManifestFetchResult()
}

sealed class PackageDownloadResult {
    data class Downloaded(
        val bodyUtf8: String,
        val sha256Hex: String,
    ) : PackageDownloadResult()

    data object Offline : PackageDownloadResult()

    data class Failed(
        val detail: String,
    ) : PackageDownloadResult()
}
