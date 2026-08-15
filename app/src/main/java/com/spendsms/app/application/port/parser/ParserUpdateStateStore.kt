package com.spendsms.app.application.port.parser

/**
 * Local cache for parser-update polling (ETag, last check, anti-replay watermark).
 * No financial data.
 */
interface ParserUpdateStateStore {

    suspend fun lastManifestEtag(): String?

    suspend fun lastCheckEpochMillis(): Long?

    /** Highest parser version that was successfully activated from a remote update. */
    suspend fun highestAcceptedParserVersion(): String?

    suspend fun recordManifestCheck(
        etag: String?,
        checkedAtEpochMillis: Long,
    )

    suspend fun recordSuccessfulActivation(
        parserVersion: String,
        etag: String?,
        activatedAtEpochMillis: Long,
    )
}
