package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion

/**
 * Static CloudFront/S3 parser manifest (Step-4 §8). Not a Lambda API response.
 */
data class ParserManifest(
    val schemaVersion: Int,
    val latestParserVersion: ParserVersion,
    val latestRulesVersion: RulesVersion,
    val minimumAppVersion: String,
    /** Absolute HTTPS URL to the signed parser-rule package (immutable path). */
    val packageUrl: String,
    /** SHA-256 hex of the package body bytes (pre-signature integrity of the download). */
    val packageSha256: String,
    val publishedAtEpochMillis: Long? = null,
) {
    init {
        require(schemaVersion >= 1) { "schemaVersion must be >= 1" }
        require(minimumAppVersion.isNotBlank()) { "minimumAppVersion must not be blank" }
        require(packageUrl.startsWith("https://")) { "packageUrl must be HTTPS" }
        require(packageSha256.matches(SHA256_HEX)) {
            "packageSha256 must be a lowercase 64-char hex digest"
        }
    }

    companion object {
        private val SHA256_HEX = Regex("^[a-f0-9]{64}$")
    }
}
