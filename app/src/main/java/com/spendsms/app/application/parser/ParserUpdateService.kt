package com.spendsms.app.application.parser

import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion

/**
 * Secure remote parser distribution on top of [ParserBundleManager]
 * (Step-2 §5.4 / Step-3 §3.11 / Step-4 §8).
 */
interface ParserUpdateService {

    /**
     * Ensure the bundled (or last known-good) parser is ACTIVE before any network work.
     */
    suspend fun ensureBundledParserReady(): ParserBundleActivationResult

    /**
     * Check CDN manifest and, when appropriate, download / verify / atomically activate.
     * Network failure keeps the current ACTIVE package.
     *
     * @param force when true, bypass the 6–24h check cadence (still uses If-None-Match).
     */
    suspend fun checkAndApplyUpdate(force: Boolean = false): ParserUpdateOutcome
}

sealed class ParserUpdateOutcome {
    data class Updated(
        val metadata: ParserMetadata,
        val previousVersion: ParserVersion?,
    ) : ParserUpdateOutcome()

    data class AlreadyCurrent(
        val activeVersion: ParserVersion?,
        val detail: String,
    ) : ParserUpdateOutcome()

    data class Skipped(
        val reason: ParserUpdateSkipReason,
        val detail: String,
        val activeVersion: ParserVersion?,
    ) : ParserUpdateOutcome()

    data class Rejected(
        val reason: ParserUpdateRejectReason,
        val detail: String,
        val activeVersion: ParserVersion?,
        val rolledBackTo: ParserVersion?,
    ) : ParserUpdateOutcome()

    data class Failed(
        val detail: String,
        val activeVersion: ParserVersion?,
    ) : ParserUpdateOutcome()
}

enum class ParserUpdateSkipReason {
    FEATURE_DISABLED,
    RECENTLY_CHECKED,
    OFFLINE,
}

enum class ParserUpdateRejectReason {
    DOWNGRADE_OR_REPLAY,
    INCOMPATIBLE_APP_VERSION,
    INCOMPATIBLE_SCHEMA,
    PACKAGE_CORRUPT,
    BAD_SIGNATURE,
    VALIDATION_FAILED,
    ACTIVATION_FAILED,
    MANIFEST_INVALID,
    DOWNLOAD_FAILED,
}
