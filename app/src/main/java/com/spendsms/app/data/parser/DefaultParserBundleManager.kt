package com.spendsms.app.data.parser

import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserBundleFailureReason
import com.spendsms.app.application.parser.ParserBundleInstallResult
import com.spendsms.app.application.parser.ParserBundleManager
import com.spendsms.app.application.parser.ParserBundleValidationResult
import com.spendsms.app.application.port.ParserBundleRepository
import com.spendsms.app.application.port.ParserRulesDocument
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.ParserBundleStatus
import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultParserBundleManager @Inject constructor(
    private val repository: ParserBundleRepository,
    private val codec: ParserRulesCodec,
    private val validator: ParserRulesValidator,
    private val signatureVerifier: ParserBundleSignatureVerifier,
    private val appVersionCompatibility: AppVersionCompatibility,
    private val bundledProvider: BundledParserRulesProvider,
    private val clock: ParserBundleClock,
    private val appVersionProvider: AppVersionProvider,
) : ParserBundleManager {

    override fun validateSignedBundle(bundleJsonUtf8: String): ParserBundleValidationResult {
        val bundle = try {
            codec.decodeSignedBundle(bundleJsonUtf8)
        } catch (e: ParserRulesCodecException) {
            return ParserBundleValidationResult.Invalid(
                reason = ParserBundleFailureReason.MALFORMED_JSON,
                detail = e.message ?: "Malformed signed bundle JSON",
            )
        } catch (e: IllegalArgumentException) {
            return ParserBundleValidationResult.Invalid(
                reason = ParserBundleFailureReason.INVALID_SCHEMA,
                detail = e.message ?: "Invalid signed bundle values",
            )
        }

        when (val envelope = validator.validateEnvelopeVersions(bundle)) {
            is ParserRulesValidator.ValidationOutcome.Failure -> return envelope.toInvalid()
            ParserRulesValidator.ValidationOutcome.Success -> Unit
        }

        val currentAppVersion = appVersionProvider.appVersionName()
        if (!appVersionCompatibility.isCompatible(currentAppVersion, bundle.minimumAppVersion)) {
            return ParserBundleValidationResult.Invalid(
                reason = ParserBundleFailureReason.INCOMPATIBLE_APP_VERSION,
                detail = "App $currentAppVersion < minimum ${bundle.minimumAppVersion}",
            )
        }

        when (val checksum = validator.validateChecksum(bundle.rulesUtf8, bundle.checksumSha256)) {
            is ParserRulesValidator.ValidationOutcome.Failure -> return checksum.toInvalid()
            ParserRulesValidator.ValidationOutcome.Success -> Unit
        }

        when (val signature = signatureVerifier.verify(bundle.rulesUtf8, bundle.signatureBase64)) {
            is ParserBundleSignatureVerifier.SignatureOutcome.Invalid ->
                return ParserBundleValidationResult.Invalid(
                    reason = ParserBundleFailureReason.SIGNATURE_INVALID,
                    detail = signature.detail,
                )
            ParserBundleSignatureVerifier.SignatureOutcome.Valid -> Unit
        }

        val rules = try {
            codec.decodeRules(bundle.rulesUtf8)
        } catch (e: ParserRulesCodecException) {
            return ParserBundleValidationResult.Invalid(
                reason = ParserBundleFailureReason.MALFORMED_JSON,
                detail = e.message ?: "Malformed rules JSON",
            )
        } catch (e: IllegalArgumentException) {
            return ParserBundleValidationResult.Invalid(
                reason = ParserBundleFailureReason.INVALID_SCHEMA,
                detail = e.message ?: "Invalid rules values",
            )
        }

        when (
            val rulesOutcome = validator.validateRulesDocument(
                rules = rules,
                expectedParserVersion = bundle.parserVersion.value,
                expectedRulesVersion = bundle.rulesVersion.value,
            )
        ) {
            is ParserRulesValidator.ValidationOutcome.Failure -> return rulesOutcome.toInvalid()
            ParserRulesValidator.ValidationOutcome.Success -> Unit
        }

        return ParserBundleValidationResult.Valid(bundle = bundle, rules = rules)
    }

    override suspend fun installVerifiedBundle(bundleJsonUtf8: String): ParserBundleInstallResult {
        return when (val validation = validateSignedBundle(bundleJsonUtf8)) {
            is ParserBundleValidationResult.Invalid ->
                ParserBundleInstallResult.Rejected(validation.reason, validation.detail)
            is ParserBundleValidationResult.Valid -> {
                val metadata = ParserMetadata(
                    parserVersion = validation.bundle.parserVersion,
                    rulesVersion = validation.bundle.rulesVersion,
                    schemaVersion = validation.rules.schemaVersion,
                    checksum = validation.bundle.checksumSha256.lowercase(),
                    installedAt = EpochMillis.of(clock.nowMillis()),
                    activatedAt = null,
                    status = ParserBundleStatus.INSTALLED,
                )
                repository.install(
                    metadata = metadata,
                    rulesDocument = ParserRulesDocument.of(validation.bundle.rulesUtf8),
                )
                ParserBundleInstallResult.Installed(metadata)
            }
        }
    }

    override suspend fun activateVerified(version: ParserVersion): ParserBundleActivationResult {
        val metadata = repository.findMetadata(version)
            ?: return fallbackToBundled(
                cause = ParserBundleFailureReason.MISSING_RULES_DOCUMENT to
                    "No installed metadata for ${version.value}",
            )

        val document = repository.loadRulesDocument(version)
            ?: return markInvalidAndFallback(
                version = version,
                reason = ParserBundleFailureReason.MISSING_RULES_DOCUMENT,
                detail = "Rules document missing for ${version.value}",
            )

        val recheck = revalidateInstalled(metadata, document.utf8Json)
        if (recheck != null) {
            return markInvalidAndFallback(version, recheck.first, recheck.second)
        }

        repository.activate(version)
        val active = repository.findActiveMetadata()
            ?: return ParserBundleActivationResult.Failed(
                reason = ParserBundleFailureReason.TAMPERED_OR_CORRUPT,
                detail = "Activation did not yield an ACTIVE metadata row",
            )
        return ParserBundleActivationResult.Activated(
            metadata = active,
            rules = codec.decodeRules(document.utf8Json),
            usedBundledFallback = false,
        )
    }

    override suspend fun ensureActiveOrFallbackToBundled(): ParserBundleActivationResult {
        val active = repository.findActiveMetadata()
        if (active != null) {
            val document = repository.loadRulesDocument(active.parserVersion)
            if (document != null) {
                val recheck = revalidateInstalled(active, document.utf8Json)
                if (recheck == null) {
                    return ParserBundleActivationResult.Activated(
                        metadata = active,
                        rules = codec.decodeRules(document.utf8Json),
                        usedBundledFallback = false,
                    )
                }
                repository.markRollback(active.parserVersion)
            }
        }
        return fallbackToBundled(cause = null)
    }

    override suspend fun loadActiveRules(): DeclarativeParserRules? {
        val document = repository.loadActiveRulesDocument() ?: return null
        return try {
            codec.decodeRules(document.utf8Json)
        } catch (_: Exception) {
            null
        }
    }

    private fun revalidateInstalled(
        metadata: ParserMetadata,
        rulesUtf8: String,
    ): Pair<ParserBundleFailureReason, String>? {
        when (val checksum = validator.validateChecksum(rulesUtf8, metadata.checksum)) {
            is ParserRulesValidator.ValidationOutcome.Failure ->
                return ParserBundleFailureReason.CHECKSUM_MISMATCH to checksum.detail
            ParserRulesValidator.ValidationOutcome.Success -> Unit
        }
        val rules = try {
            codec.decodeRules(rulesUtf8)
        } catch (e: Exception) {
            return ParserBundleFailureReason.TAMPERED_OR_CORRUPT to
                (e.message ?: "Corrupt rules document")
        }
        when (
            val outcome = validator.validateRulesDocument(
                rules = rules,
                expectedParserVersion = metadata.parserVersion.value,
                expectedRulesVersion = metadata.rulesVersion.value,
            )
        ) {
            is ParserRulesValidator.ValidationOutcome.Failure ->
                return outcome.code.toFailureReason() to outcome.detail
            ParserRulesValidator.ValidationOutcome.Success -> Unit
        }
        return null
    }

    private suspend fun markInvalidAndFallback(
        version: ParserVersion,
        reason: ParserBundleFailureReason,
        detail: String,
    ): ParserBundleActivationResult {
        repository.markInvalid(version)
        return fallbackToBundled(cause = reason to detail)
    }

    private suspend fun fallbackToBundled(
        cause: Pair<ParserBundleFailureReason, String>?,
    ): ParserBundleActivationResult {
        val bundledJson = try {
            bundledProvider.loadSignedBundleJson()
        } catch (e: Exception) {
            return ParserBundleActivationResult.Failed(
                reason = ParserBundleFailureReason.BUNDLED_FALLBACK_UNAVAILABLE,
                detail = cause?.second ?: (e.message ?: "Bundled parser rules unavailable"),
            )
        }

        return when (val install = installVerifiedBundle(bundledJson)) {
            is ParserBundleInstallResult.Rejected ->
                ParserBundleActivationResult.Failed(
                    reason = ParserBundleFailureReason.BUNDLED_FALLBACK_UNAVAILABLE,
                    detail = buildString {
                        append(install.reason).append(": ").append(install.detail)
                        if (cause != null) {
                            append(" (cause: ").append(cause.first).append(": ")
                                .append(cause.second).append(')')
                        }
                    },
                )
            is ParserBundleInstallResult.Installed -> {
                repository.activate(install.metadata.parserVersion)
                val active = repository.findActiveMetadata()
                    ?: return ParserBundleActivationResult.Failed(
                        reason = ParserBundleFailureReason.BUNDLED_FALLBACK_UNAVAILABLE,
                        detail = "Bundled rules installed but activation failed",
                    )
                val rulesUtf8 = repository.loadRulesDocument(active.parserVersion)?.utf8Json
                    ?: return ParserBundleActivationResult.Failed(
                        reason = ParserBundleFailureReason.BUNDLED_FALLBACK_UNAVAILABLE,
                        detail = "Bundled rules file missing after activation",
                    )
                ParserBundleActivationResult.Activated(
                    metadata = active,
                    rules = codec.decodeRules(rulesUtf8),
                    usedBundledFallback = true,
                )
            }
        }
    }
}

private fun ParserRulesValidator.ValidationOutcome.Failure.toInvalid():
    ParserBundleValidationResult.Invalid =
    ParserBundleValidationResult.Invalid(
        reason = code.toFailureReason(),
        detail = detail,
    )

private fun ParserRulesValidator.FailureCode.toFailureReason(): ParserBundleFailureReason =
    when (this) {
        ParserRulesValidator.FailureCode.INCOMPATIBLE_SCHEMA ->
            ParserBundleFailureReason.INCOMPATIBLE_SCHEMA
        ParserRulesValidator.FailureCode.CHECKSUM_MISMATCH ->
            ParserBundleFailureReason.CHECKSUM_MISMATCH
        ParserRulesValidator.FailureCode.VALIDATION_FAILED ->
            ParserBundleFailureReason.VALIDATION_FAILED
    }
