package com.spendsms.app.application.parser

import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.SignedParserBundle

/**
 * Local parser-rule install / verify / activate / fallback (Step-3 Parser Bundle Manager).
 *
 * Does not download packages or parse SMS — only validates declarative data and
 * activates through [com.spendsms.app.application.port.ParserBundleRepository].
 */
interface ParserBundleManager {

    /**
     * Decode + fully validate a signed package (signature, checksum, schema,
     * min app version, declarative shape) without installing.
     */
    fun validateSignedBundle(bundleJsonUtf8: String): ParserBundleValidationResult

    /**
     * Install a verified signed package as INSTALLED (not yet ACTIVE).
     */
    suspend fun installVerifiedBundle(bundleJsonUtf8: String): ParserBundleInstallResult

    /**
     * Activate an already-installed version after re-verifying the on-disk rules
     * against the stored metadata checksum and current public-key policy.
     * On failure, marks the candidate INVALID when possible and falls back to
     * bundled defaults.
     */
    suspend fun activateVerified(version: ParserVersion): ParserBundleActivationResult

    /**
     * Ensure an ACTIVE bundle exists. If none is active, or the active document
     * fails re-validation, install and activate the APK-bundled defaults.
     */
    suspend fun ensureActiveOrFallbackToBundled(): ParserBundleActivationResult

    /**
     * Load and decode the currently ACTIVE rules document, or null if none.
     */
    suspend fun loadActiveRules(): DeclarativeParserRules?
}

sealed class ParserBundleValidationResult {
    data class Valid(
        val bundle: SignedParserBundle,
        val rules: DeclarativeParserRules,
    ) : ParserBundleValidationResult()

    data class Invalid(
        val reason: ParserBundleFailureReason,
        val detail: String,
    ) : ParserBundleValidationResult()
}

sealed class ParserBundleInstallResult {
    data class Installed(val metadata: ParserMetadata) : ParserBundleInstallResult()

    data class Rejected(
        val reason: ParserBundleFailureReason,
        val detail: String,
    ) : ParserBundleInstallResult()
}

sealed class ParserBundleActivationResult {
    data class Activated(
        val metadata: ParserMetadata,
        val rules: DeclarativeParserRules,
        val usedBundledFallback: Boolean,
    ) : ParserBundleActivationResult()

    data class Failed(
        val reason: ParserBundleFailureReason,
        val detail: String,
    ) : ParserBundleActivationResult()
}

enum class ParserBundleFailureReason {
    MALFORMED_JSON,
    INVALID_SCHEMA,
    INCOMPATIBLE_SCHEMA,
    INCOMPATIBLE_APP_VERSION,
    CHECKSUM_MISMATCH,
    SIGNATURE_INVALID,
    TAMPERED_OR_CORRUPT,
    VALIDATION_FAILED,
    MISSING_RULES_DOCUMENT,
    BUNDLED_FALLBACK_UNAVAILABLE,
}
