package com.spendsms.app.data.parser.update

import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserBundleFailureReason
import com.spendsms.app.application.parser.ParserBundleInstallResult
import com.spendsms.app.application.parser.ParserBundleManager
import com.spendsms.app.application.parser.ParserUpdateOutcome
import com.spendsms.app.application.parser.ParserUpdateRejectReason
import com.spendsms.app.application.parser.ParserUpdateService
import com.spendsms.app.application.parser.ParserUpdateSkipReason
import com.spendsms.app.application.port.ParserBundleRepository
import com.spendsms.app.application.port.config.RemoteConfigPort
import com.spendsms.app.application.port.parser.ManifestFetchResult
import com.spendsms.app.application.port.parser.PackageDownloadResult
import com.spendsms.app.application.port.parser.ParserUpdateRemotePort
import com.spendsms.app.application.port.parser.ParserUpdateStateStore
import com.spendsms.app.data.parser.AppVersionCompatibility
import com.spendsms.app.data.parser.AppVersionProvider
import com.spendsms.app.data.parser.ParserBundleClock
import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.parsing.ParserManifest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates manifest check → download → Prompt-6 validate/install/activate
 * with offline keep-alive, anti-downgrade/replay, and last-known-good rollback.
 */
@Singleton
class DefaultParserUpdateService @Inject constructor(
    private val bundleManager: ParserBundleManager,
    private val repository: ParserBundleRepository,
    private val remote: ParserUpdateRemotePort,
    private val stateStore: ParserUpdateStateStore,
    private val remoteConfig: RemoteConfigPort,
    private val appVersionProvider: AppVersionProvider,
    private val appVersionCompatibility: AppVersionCompatibility,
    private val clock: ParserBundleClock,
) : ParserUpdateService {

    override suspend fun ensureBundledParserReady(): ParserBundleActivationResult =
        bundleManager.ensureActiveOrFallbackToBundled()

    override suspend fun checkAndApplyUpdate(force: Boolean): ParserUpdateOutcome {
        val startup = ensureBundledParserReady()
        if (startup is ParserBundleActivationResult.Failed) {
            return ParserUpdateOutcome.Failed(
                detail = startup.detail,
                activeVersion = null,
            )
        }

        val activeBefore = repository.findActiveMetadata()
        val activeVersion = activeBefore?.parserVersion

        if (!remoteConfig.getConfig().newParserVersionEnabled) {
            return ParserUpdateOutcome.Skipped(
                reason = ParserUpdateSkipReason.FEATURE_DISABLED,
                detail = "newParserVersionEnabled=false",
                activeVersion = activeVersion,
            )
        }

        val now = clock.nowMillis()
        if (!force && wasCheckedRecently(now)) {
            return ParserUpdateOutcome.Skipped(
                reason = ParserUpdateSkipReason.RECENTLY_CHECKED,
                detail = "Within ${CHECK_INTERVAL_HOURS}h parser update cadence",
                activeVersion = activeVersion,
            )
        }

        return when (val fetch = remote.fetchManifest(stateStore.lastManifestEtag())) {
            ManifestFetchResult.Offline ->
                ParserUpdateOutcome.Skipped(
                    reason = ParserUpdateSkipReason.OFFLINE,
                    detail = "Parser CDN unreachable; keeping active package",
                    activeVersion = activeVersion,
                )

            is ManifestFetchResult.Failed ->
                ParserUpdateOutcome.Failed(
                    detail = fetch.detail,
                    activeVersion = activeVersion,
                )

            is ManifestFetchResult.NotModified -> {
                stateStore.recordManifestCheck(etag = fetch.etag, checkedAtEpochMillis = now)
                ParserUpdateOutcome.AlreadyCurrent(
                    activeVersion = activeVersion,
                    detail = "Manifest Not Modified (ETag)",
                )
            }

            is ManifestFetchResult.Available ->
                applyManifest(
                    manifest = fetch.manifest,
                    etag = fetch.etag,
                    activeBefore = activeBefore,
                    now = now,
                )
        }
    }

    private suspend fun applyManifest(
        manifest: ParserManifest,
        etag: String?,
        activeBefore: ParserMetadata?,
        now: Long,
    ): ParserUpdateOutcome {
        val activeVersion = activeBefore?.parserVersion

        if (manifest.schemaVersion > SUPPORTED_MANIFEST_SCHEMA) {
            stateStore.recordManifestCheck(etag = etag, checkedAtEpochMillis = now)
            return ParserUpdateOutcome.Rejected(
                reason = ParserUpdateRejectReason.INCOMPATIBLE_SCHEMA,
                detail = "Unsupported manifest schema ${manifest.schemaVersion}",
                activeVersion = activeVersion,
                rolledBackTo = null,
            )
        }

        val appVersion = appVersionProvider.appVersionName()
        if (!appVersionCompatibility.isCompatible(appVersion, manifest.minimumAppVersion)) {
            stateStore.recordManifestCheck(etag = etag, checkedAtEpochMillis = now)
            return ParserUpdateOutcome.Rejected(
                reason = ParserUpdateRejectReason.INCOMPATIBLE_APP_VERSION,
                detail = "App $appVersion < manifest minimum ${manifest.minimumAppVersion}",
                activeVersion = activeVersion,
                rolledBackTo = null,
            )
        }

        val candidate = manifest.latestParserVersion.value
        if (isDowngradeOrReplay(candidate, activeVersion)) {
            stateStore.recordManifestCheck(etag = etag, checkedAtEpochMillis = now)
            return ParserUpdateOutcome.AlreadyCurrent(
                activeVersion = activeVersion,
                detail = "No newer parser version (anti-downgrade/replay)",
            )
        }

        return when (val download = remote.downloadPackage(manifest.packageUrl)) {
            PackageDownloadResult.Offline ->
                ParserUpdateOutcome.Skipped(
                    reason = ParserUpdateSkipReason.OFFLINE,
                    detail = "Package download unreachable; keeping active package",
                    activeVersion = activeVersion,
                )

            is PackageDownloadResult.Failed ->
                ParserUpdateOutcome.Rejected(
                    reason = ParserUpdateRejectReason.DOWNLOAD_FAILED,
                    detail = download.detail,
                    activeVersion = activeVersion,
                    rolledBackTo = null,
                )

            is PackageDownloadResult.Downloaded ->
                installAndActivate(
                    download = download,
                    expectedSha256 = manifest.packageSha256,
                    etag = etag,
                    activeBefore = activeBefore,
                    now = now,
                )
        }
    }

    private suspend fun installAndActivate(
        download: PackageDownloadResult.Downloaded,
        expectedSha256: String,
        etag: String?,
        activeBefore: ParserMetadata?,
        now: Long,
    ): ParserUpdateOutcome {
        val activeVersion = activeBefore?.parserVersion

        if (!download.sha256Hex.equals(expectedSha256, ignoreCase = true)) {
            return ParserUpdateOutcome.Rejected(
                reason = ParserUpdateRejectReason.PACKAGE_CORRUPT,
                detail = "Downloaded package SHA-256 mismatch",
                activeVersion = activeVersion,
                rolledBackTo = null,
            )
        }

        when (val install = bundleManager.installVerifiedBundle(download.bodyUtf8)) {
            is ParserBundleInstallResult.Rejected -> {
                return ParserUpdateOutcome.Rejected(
                    reason = install.reason.toUpdateRejectReason(),
                    detail = install.detail,
                    activeVersion = activeVersion,
                    rolledBackTo = null,
                )
            }

            is ParserBundleInstallResult.Installed -> {
                val activation = bundleManager.activateVerified(install.metadata.parserVersion)
                return when (activation) {
                    is ParserBundleActivationResult.Activated -> {
                        if (activation.usedBundledFallback) {
                            val restored = restorePreviousOrBundled(activeBefore)
                            return ParserUpdateOutcome.Rejected(
                                reason = ParserUpdateRejectReason.ACTIVATION_FAILED,
                                detail = "Activation fell back unexpectedly",
                                activeVersion = restored?.parserVersion ?: activeVersion,
                                rolledBackTo = restored?.parserVersion,
                            )
                        }
                        stateStore.recordSuccessfulActivation(
                            parserVersion = activation.metadata.parserVersion.value,
                            etag = etag,
                            activatedAtEpochMillis = now,
                        )
                        ParserUpdateOutcome.Updated(
                            metadata = activation.metadata,
                            previousVersion = activeVersion,
                        )
                    }

                    is ParserBundleActivationResult.Failed -> {
                        val restored = restorePreviousOrBundled(activeBefore)
                        ParserUpdateOutcome.Rejected(
                            reason = ParserUpdateRejectReason.ACTIVATION_FAILED,
                            detail = activation.detail,
                            activeVersion = restored?.parserVersion ?: activeVersion,
                            rolledBackTo = restored?.parserVersion,
                        )
                    }
                }
            }
        }
    }

    private suspend fun restorePreviousOrBundled(
        previous: ParserMetadata?,
    ): ParserMetadata? {
        if (previous != null) {
            when (val restore = bundleManager.activateVerified(previous.parserVersion)) {
                is ParserBundleActivationResult.Activated -> {
                    if (!restore.usedBundledFallback) {
                        return restore.metadata
                    }
                }
                is ParserBundleActivationResult.Failed -> Unit
            }
        }
        return when (val fallback = bundleManager.ensureActiveOrFallbackToBundled()) {
            is ParserBundleActivationResult.Activated -> fallback.metadata
            is ParserBundleActivationResult.Failed -> null
        }
    }

    private suspend fun isDowngradeOrReplay(
        candidate: String,
        active: ParserVersion?,
    ): Boolean {
        if (active != null && !ParserVersionOrdering.isNewer(candidate, active.value)) {
            return true
        }
        val highest = stateStore.highestAcceptedParserVersion()
        if (highest != null && !ParserVersionOrdering.isNewer(candidate, highest)) {
            return true
        }
        return false
    }

    private suspend fun wasCheckedRecently(now: Long): Boolean {
        val last = stateStore.lastCheckEpochMillis() ?: return false
        return now - last < TimeUnit.HOURS.toMillis(CHECK_INTERVAL_HOURS)
    }

    companion object {
        const val SUPPORTED_MANIFEST_SCHEMA = 1
        /** Midpoint of the approved 6–24h configuration cache window. */
        const val CHECK_INTERVAL_HOURS = 12L
    }
}

private fun ParserBundleFailureReason.toUpdateRejectReason(): ParserUpdateRejectReason =
    when (this) {
        ParserBundleFailureReason.SIGNATURE_INVALID ->
            ParserUpdateRejectReason.BAD_SIGNATURE
        ParserBundleFailureReason.CHECKSUM_MISMATCH,
        ParserBundleFailureReason.TAMPERED_OR_CORRUPT,
        ParserBundleFailureReason.MALFORMED_JSON,
        -> ParserUpdateRejectReason.PACKAGE_CORRUPT
        ParserBundleFailureReason.INCOMPATIBLE_APP_VERSION ->
            ParserUpdateRejectReason.INCOMPATIBLE_APP_VERSION
        ParserBundleFailureReason.INCOMPATIBLE_SCHEMA,
        ParserBundleFailureReason.INVALID_SCHEMA,
        -> ParserUpdateRejectReason.INCOMPATIBLE_SCHEMA
        ParserBundleFailureReason.VALIDATION_FAILED,
        ParserBundleFailureReason.MISSING_RULES_DOCUMENT,
        ParserBundleFailureReason.BUNDLED_FALLBACK_UNAVAILABLE,
        -> ParserUpdateRejectReason.VALIDATION_FAILED
    }
