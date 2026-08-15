package com.spendsms.app.data.parser.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserBundleInstallResult
import com.spendsms.app.application.parser.ParserBundleManager
import com.spendsms.app.application.parser.ParserUpdateOutcome
import com.spendsms.app.application.parser.ParserUpdateRejectReason
import com.spendsms.app.application.parser.ParserUpdateSkipReason
import com.spendsms.app.application.port.ParserRulesDocument
import com.spendsms.app.application.port.config.AppRemoteConfig
import com.spendsms.app.application.port.config.RemoteConfigPort
import com.spendsms.app.application.port.parser.ManifestFetchResult
import com.spendsms.app.application.port.parser.PackageDownloadResult
import com.spendsms.app.application.port.parser.ParserUpdateRemotePort
import com.spendsms.app.application.port.parser.ParserUpdateStateStore
import com.spendsms.app.data.parser.AppVersionCompatibility
import com.spendsms.app.data.parser.AppVersionProvider
import com.spendsms.app.data.parser.AssetParserSigningPublicKeyProvider
import com.spendsms.app.data.parser.BundledParserRulesProvider
import com.spendsms.app.data.parser.DefaultParserBundleManager
import com.spendsms.app.data.parser.ParserAssetLoader
import com.spendsms.app.data.parser.ParserBundleClock
import com.spendsms.app.data.parser.ParserBundleSignatureVerifier
import com.spendsms.app.data.parser.ParserBundleTestFixtures
import com.spendsms.app.data.parser.ParserRulesCodec
import com.spendsms.app.data.parser.ParserRulesValidator
import com.spendsms.app.data.repository.RoomParserBundleRepository
import com.spendsms.app.data.room.SpendSmsDatabase
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.parsing.ParserManifest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ParserUpdateServiceTest {

    private lateinit var db: SpendSmsDatabase
    private lateinit var repository: RoomParserBundleRepository
    private lateinit var manager: DefaultParserBundleManager
    private lateinit var codec: ParserRulesCodec
    private lateinit var remote: FakeParserUpdateRemote
    private lateinit var stateStore: InMemoryParserUpdateStateStore
    private lateinit var remoteConfig: MutableRemoteConfigPort
    private lateinit var clock: MutableClock
    private lateinit var service: DefaultParserUpdateService

    private val packageUrl = "https://cdn.example.invalid/parser/2026.08.13.1/bundle.json"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = SpendSmsDatabase.buildInMemory(context)
        repository = RoomParserBundleRepository(context, db.parserMetadataDao())
        codec = ParserBundleTestFixtures.codec()
        val assetLoader = ParserAssetLoader(context)
        manager = DefaultParserBundleManager(
            repository = repository,
            codec = codec,
            validator = ParserRulesValidator(),
            signatureVerifier = ParserBundleSignatureVerifier(
                AssetParserSigningPublicKeyProvider(assetLoader),
            ),
            appVersionCompatibility = AppVersionCompatibility(),
            bundledProvider = BundledParserRulesProvider(assetLoader, codec),
            clock = ParserBundleClock { 1_700_000_000_000L },
            appVersionProvider = AppVersionProvider { "0.1.0-phase0" },
        )
        remote = FakeParserUpdateRemote()
        stateStore = InMemoryParserUpdateStateStore()
        remoteConfig = MutableRemoteConfigPort(AppRemoteConfig.bundledDefaults())
        clock = MutableClock(1_700_000_000_000L)
        service = DefaultParserUpdateService(
            bundleManager = manager,
            repository = repository,
            remote = remote,
            stateStore = stateStore,
            remoteConfig = remoteConfig,
            appVersionProvider = AppVersionProvider { "0.1.0-phase0" },
            appVersionCompatibility = AppVersionCompatibility(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun bundledStartup_activatesDefaultPackage() = runBlocking {
        val result = service.ensureBundledParserReady()
        assertThat(result).isInstanceOf(ParserBundleActivationResult.Activated::class.java)
        val activated = result as ParserBundleActivationResult.Activated
        assertThat(activated.usedBundledFallback).isTrue()
        assertThat(activated.metadata.parserVersion.value).isEqualTo("bundled-2026.08.12.0")
        assertThat(repository.findActiveMetadata()?.parserVersion?.value)
            .isEqualTo("bundled-2026.08.12.0")
    }

    @Test
    fun successfulUpdate_downloadsVerifiesAndActivates() = runBlocking {
        service.ensureBundledParserReady()
        val previous = repository.findActiveMetadata()!!.parserVersion

        val bundleJson = signedRemoteBundle(
            parserVersion = "2026.08.13.1",
            rulesVersion = "remote-rules-1",
        )
        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(bundleJson, version = "2026.08.13.1"),
            etag = "\"etag-v2\"",
        )
        remote.packageResult = PackageDownloadResult.Downloaded(
            bodyUtf8 = bundleJson,
            sha256Hex = ParserRulesValidator.sha256Hex(bundleJson.toByteArray(Charsets.UTF_8)),
        )

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Updated::class.java)
        val updated = outcome as ParserUpdateOutcome.Updated
        assertThat(updated.metadata.parserVersion.value).isEqualTo("2026.08.13.1")
        assertThat(updated.previousVersion).isEqualTo(previous)
        assertThat(repository.findActiveMetadata()?.parserVersion?.value)
            .isEqualTo("2026.08.13.1")
        assertThat(stateStore.highestAcceptedParserVersion()).isEqualTo("2026.08.13.1")
        assertThat(stateStore.lastManifestEtag()).isEqualTo("\"etag-v2\"")
    }

    @Test
    fun noUpdate_whenManifestNotModified() = runBlocking {
        service.ensureBundledParserReady()
        stateStore.recordManifestCheck(etag = "\"same\"", checkedAtEpochMillis = 0L)
        remote.manifestResult = ManifestFetchResult.NotModified(etag = "\"same\"")

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.AlreadyCurrent::class.java)
        assertThat(remote.downloadCalls).isEqualTo(0)
    }

    @Test
    fun noUpdate_whenRemoteVersionNotNewer() = runBlocking {
        service.ensureBundledParserReady()
        val bundleJson = ParserBundleTestFixtures.signedBundleJson(
            parserVersion = "bundled-2026.08.12.0",
            rulesVersion = "bundled-rules-1",
        )
        // Same numeric core as bundled → anti-downgrade / no-op
        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(bundleJson, version = "bundled-2026.08.12.0"),
            etag = "\"same-ver\"",
        )

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.AlreadyCurrent::class.java)
        assertThat(remote.downloadCalls).isEqualTo(0)
    }

    @Test
    fun offline_keepsActivePackage() = runBlocking {
        service.ensureBundledParserReady()
        val before = repository.findActiveMetadata()!!.parserVersion
        remote.manifestResult = ManifestFetchResult.Offline

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Skipped::class.java)
        val skipped = outcome as ParserUpdateOutcome.Skipped
        assertThat(skipped.reason).isEqualTo(ParserUpdateSkipReason.OFFLINE)
        assertThat(repository.findActiveMetadata()?.parserVersion).isEqualTo(before)
    }

    @Test
    fun badSignature_rejectsWithoutChangingActive() = runBlocking {
        service.ensureBundledParserReady()
        val before = repository.findActiveMetadata()!!.parserVersion

        val badJson = signedRemoteBundle(
            parserVersion = "2026.08.13.2",
            rulesVersion = "remote-rules-bad-sig",
            signatureOverride = "AAAA",
        )
        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(badJson, version = "2026.08.13.2"),
            etag = "\"bad-sig\"",
        )
        remote.packageResult = PackageDownloadResult.Downloaded(
            bodyUtf8 = badJson,
            sha256Hex = ParserRulesValidator.sha256Hex(badJson.toByteArray(Charsets.UTF_8)),
        )

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Rejected::class.java)
        assertThat((outcome as ParserUpdateOutcome.Rejected).reason)
            .isEqualTo(ParserUpdateRejectReason.BAD_SIGNATURE)
        assertThat(repository.findActiveMetadata()?.parserVersion).isEqualTo(before)
    }

    @Test
    fun corruption_rejectsOnPackageShaMismatch() = runBlocking {
        service.ensureBundledParserReady()
        val before = repository.findActiveMetadata()!!.parserVersion

        val bundleJson = signedRemoteBundle(
            parserVersion = "2026.08.13.3",
            rulesVersion = "remote-rules-corrupt",
        )
        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(bundleJson, version = "2026.08.13.3")
                .copy(packageSha256 = "0".repeat(64)),
            etag = "\"corrupt\"",
        )
        remote.packageResult = PackageDownloadResult.Downloaded(
            bodyUtf8 = bundleJson,
            sha256Hex = ParserRulesValidator.sha256Hex(bundleJson.toByteArray(Charsets.UTF_8)),
        )

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Rejected::class.java)
        assertThat((outcome as ParserUpdateOutcome.Rejected).reason)
            .isEqualTo(ParserUpdateRejectReason.PACKAGE_CORRUPT)
        assertThat(repository.findActiveMetadata()?.parserVersion).isEqualTo(before)
        assertThat(remote.downloadCalls).isEqualTo(1)
    }

    @Test
    fun incompatibleVersion_rejectsBeforeDownload() = runBlocking {
        service.ensureBundledParserReady()
        val before = repository.findActiveMetadata()!!.parserVersion

        val bundleJson = signedRemoteBundle(
            parserVersion = "2026.08.13.4",
            rulesVersion = "remote-rules-incompat",
            minimumAppVersion = "9.0.0",
        )
        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(bundleJson, version = "2026.08.13.4")
                .copy(minimumAppVersion = "9.0.0"),
            etag = "\"incompat\"",
        )

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Rejected::class.java)
        assertThat((outcome as ParserUpdateOutcome.Rejected).reason)
            .isEqualTo(ParserUpdateRejectReason.INCOMPATIBLE_APP_VERSION)
        assertThat(remote.downloadCalls).isEqualTo(0)
        assertThat(repository.findActiveMetadata()?.parserVersion).isEqualTo(before)
    }

    @Test
    fun failedActivation_rollsBackToPrevious() = runBlocking {
        service.ensureBundledParserReady()
        val previous = repository.findActiveMetadata()!!.parserVersion

        val bundleJson = signedRemoteBundle(
            parserVersion = "2026.08.13.5",
            rulesVersion = "remote-rules-fail-act",
        )
        // Install succeeds via manager, then corrupt on-disk rules before activation
        // by wrapping manager.
        val wrappingManager = object : ParserBundleManager by manager {
            override suspend fun installVerifiedBundle(bundleJsonUtf8: String): ParserBundleInstallResult {
                val installed = manager.installVerifiedBundle(bundleJsonUtf8)
                if (installed is ParserBundleInstallResult.Installed) {
                    // Corrupt staged rules so activateVerified fails revalidation
                    repository.install(
                        metadata = installed.metadata,
                        rulesDocument = ParserRulesDocument.of("{not-valid-rules"),
                    )
                }
                return installed
            }
        }
        val failingService = DefaultParserUpdateService(
            bundleManager = wrappingManager,
            repository = repository,
            remote = remote,
            stateStore = stateStore,
            remoteConfig = remoteConfig,
            appVersionProvider = AppVersionProvider { "0.1.0-phase0" },
            appVersionCompatibility = AppVersionCompatibility(),
            clock = clock,
        )

        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(bundleJson, version = "2026.08.13.5"),
            etag = "\"fail-act\"",
        )
        remote.packageResult = PackageDownloadResult.Downloaded(
            bodyUtf8 = bundleJson,
            sha256Hex = ParserRulesValidator.sha256Hex(bundleJson.toByteArray(Charsets.UTF_8)),
        )

        val outcome = failingService.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Rejected::class.java)
        val rejected = outcome as ParserUpdateOutcome.Rejected
        assertThat(rejected.reason).isEqualTo(ParserUpdateRejectReason.ACTIVATION_FAILED)
        assertThat(repository.findActiveMetadata()?.parserVersion).isEqualTo(previous)
        assertThat(rejected.rolledBackTo).isEqualTo(previous)
    }

    @Test
    fun rollback_prefersLastKnownGoodOverOnlyBundledWhenPreviousIntact() = runBlocking {
        // Seed bundled, then successfully activate a remote package, then fail a newer one
        service.ensureBundledParserReady()
        val v1Json = signedRemoteBundle(
            parserVersion = "2026.08.13.10",
            rulesVersion = "remote-rules-v1",
        )
        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(v1Json, version = "2026.08.13.10"),
            etag = "\"v1\"",
        )
        remote.packageResult = PackageDownloadResult.Downloaded(
            bodyUtf8 = v1Json,
            sha256Hex = ParserRulesValidator.sha256Hex(v1Json.toByteArray(Charsets.UTF_8)),
        )
        assertThat(service.checkAndApplyUpdate(force = true))
            .isInstanceOf(ParserUpdateOutcome.Updated::class.java)
        val knownGood = repository.findActiveMetadata()!!.parserVersion
        assertThat(knownGood.value).isEqualTo("2026.08.13.10")

        val v2Json = signedRemoteBundle(
            parserVersion = "2026.08.13.11",
            rulesVersion = "remote-rules-v2",
        )
        val wrappingManager = object : ParserBundleManager by manager {
            override suspend fun installVerifiedBundle(bundleJsonUtf8: String): ParserBundleInstallResult {
                val installed = manager.installVerifiedBundle(bundleJsonUtf8)
                if (installed is ParserBundleInstallResult.Installed &&
                    installed.metadata.parserVersion.value == "2026.08.13.11"
                ) {
                    repository.install(
                        metadata = installed.metadata,
                        rulesDocument = ParserRulesDocument.of("{corrupt"),
                    )
                }
                return installed
            }
        }
        val failingService = DefaultParserUpdateService(
            bundleManager = wrappingManager,
            repository = repository,
            remote = remote,
            stateStore = stateStore,
            remoteConfig = remoteConfig,
            appVersionProvider = AppVersionProvider { "0.1.0-phase0" },
            appVersionCompatibility = AppVersionCompatibility(),
            clock = clock,
        )
        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(v2Json, version = "2026.08.13.11"),
            etag = "\"v2\"",
        )
        remote.packageResult = PackageDownloadResult.Downloaded(
            bodyUtf8 = v2Json,
            sha256Hex = ParserRulesValidator.sha256Hex(v2Json.toByteArray(Charsets.UTF_8)),
        )

        val outcome = failingService.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Rejected::class.java)
        assertThat(repository.findActiveMetadata()?.parserVersion).isEqualTo(knownGood)
    }

    @Test
    fun recentlyChecked_skipsNetworkUnlessForced() = runBlocking {
        service.ensureBundledParserReady()
        stateStore.recordManifestCheck(
            etag = "\"x\"",
            checkedAtEpochMillis = clock.nowMillis(),
        )
        clock.now = clock.now + TimeUnit.HOURS.toMillis(1)

        val outcome = service.checkAndApplyUpdate(force = false)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Skipped::class.java)
        assertThat((outcome as ParserUpdateOutcome.Skipped).reason)
            .isEqualTo(ParserUpdateSkipReason.RECENTLY_CHECKED)
        assertThat(remote.manifestCalls).isEqualTo(0)
    }

    @Test
    fun featureFlagDisabled_skipsUpdate() = runBlocking {
        service.ensureBundledParserReady()
        remoteConfig.config = AppRemoteConfig.bundledDefaults().copy(newParserVersionEnabled = false)

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.Skipped::class.java)
        assertThat((outcome as ParserUpdateOutcome.Skipped).reason)
            .isEqualTo(ParserUpdateSkipReason.FEATURE_DISABLED)
        assertThat(remote.manifestCalls).isEqualTo(0)
    }

    @Test
    fun replay_rejectsOlderThanHighestAccepted() = runBlocking {
        service.ensureBundledParserReady()
        stateStore.recordSuccessfulActivation(
            parserVersion = "2026.08.14.0",
            etag = "\"high\"",
            activatedAtEpochMillis = clock.nowMillis(),
        )
        // Active is still bundled (lower); remote offers mid version < highest watermark
        val midJson = signedRemoteBundle(
            parserVersion = "2026.08.13.9",
            rulesVersion = "remote-mid",
        )
        remote.manifestResult = ManifestFetchResult.Available(
            manifest = manifestFor(midJson, version = "2026.08.13.9"),
            etag = "\"mid\"",
        )

        val outcome = service.checkAndApplyUpdate(force = true)
        assertThat(outcome).isInstanceOf(ParserUpdateOutcome.AlreadyCurrent::class.java)
        assertThat(remote.downloadCalls).isEqualTo(0)
    }

    private fun signedRemoteBundle(
        parserVersion: String,
        rulesVersion: String,
        minimumAppVersion: String = "0.1.0",
        signatureOverride: String? = null,
    ): String {
        val rulesUtf8 = ParserBundleTestFixtures.sampleRulesUtf8(
            parserVersion = parserVersion,
            rulesVersion = rulesVersion,
        )
        return ParserBundleTestFixtures.signedBundleJson(
            rulesUtf8 = rulesUtf8,
            parserVersion = parserVersion,
            rulesVersion = rulesVersion,
            minimumAppVersion = minimumAppVersion,
            signatureOverride = signatureOverride,
        )
    }

    private fun manifestFor(
        bundleJson: String,
        version: String,
    ): ParserManifest = ParserManifest(
        schemaVersion = 1,
        latestParserVersion = ParserVersion.of(version),
        latestRulesVersion = RulesVersion.of("rules-for-$version"),
        minimumAppVersion = "0.1.0",
        packageUrl = packageUrl,
        packageSha256 = ParserRulesValidator.sha256Hex(bundleJson.toByteArray(Charsets.UTF_8)),
    )
}

private class FakeParserUpdateRemote : ParserUpdateRemotePort {
    var manifestResult: ManifestFetchResult = ManifestFetchResult.Offline
    var packageResult: PackageDownloadResult = PackageDownloadResult.Offline
    var manifestCalls: Int = 0
    var downloadCalls: Int = 0
    var lastIfNoneMatch: String? = null

    override suspend fun fetchManifest(ifNoneMatch: String?): ManifestFetchResult {
        manifestCalls++
        lastIfNoneMatch = ifNoneMatch
        return manifestResult
    }

    override suspend fun downloadPackage(packageUrl: String): PackageDownloadResult {
        downloadCalls++
        return packageResult
    }
}

private class InMemoryParserUpdateStateStore : ParserUpdateStateStore {
    private var etag: String? = null
    private var lastCheck: Long? = null
    private var highest: String? = null

    override suspend fun lastManifestEtag(): String? = etag
    override suspend fun lastCheckEpochMillis(): Long? = lastCheck
    override suspend fun highestAcceptedParserVersion(): String? = highest

    override suspend fun recordManifestCheck(etag: String?, checkedAtEpochMillis: Long) {
        lastCheck = checkedAtEpochMillis
        if (etag != null) this.etag = etag
    }

    override suspend fun recordSuccessfulActivation(
        parserVersion: String,
        etag: String?,
        activatedAtEpochMillis: Long,
    ) {
        lastCheck = activatedAtEpochMillis
        highest = parserVersion
        if (etag != null) this.etag = etag
    }
}

private class MutableRemoteConfigPort(
    var config: AppRemoteConfig,
) : RemoteConfigPort {
    override suspend fun getConfig(): AppRemoteConfig = config
}

private class MutableClock(var now: Long) : ParserBundleClock {
    override fun nowMillis(): Long = now
}
