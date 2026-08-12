package com.spendsms.app.data.parser

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.parser.ParserBundleActivationResult
import com.spendsms.app.application.parser.ParserBundleFailureReason
import com.spendsms.app.application.parser.ParserBundleInstallResult
import com.spendsms.app.application.parser.ParserBundleValidationResult
import com.spendsms.app.application.port.ParserRulesDocument
import com.spendsms.app.data.repository.RoomParserBundleRepository
import com.spendsms.app.data.room.SpendSmsDatabase
import com.spendsms.app.domain.model.ParserBundleStatus
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ParserBundleManagerTest {

    private lateinit var db: SpendSmsDatabase
    private lateinit var repository: RoomParserBundleRepository
    private lateinit var manager: DefaultParserBundleManager
    private lateinit var codec: ParserRulesCodec

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun validate_acceptsValidSignedBundle() {
        val json = ParserBundleTestFixtures.signedBundleJson()
        val result = manager.validateSignedBundle(json)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Valid::class.java)
        val valid = result as ParserBundleValidationResult.Valid
        assertThat(valid.rules.institutions).isNotEmpty()
        assertThat(valid.bundle.parserVersion.value).isEqualTo("test-2026.08.12.1")
    }

    @Test
    fun validate_rejectsMalformedJson() {
        val result = manager.validateSignedBundle("{not-json")
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Invalid::class.java)
        assertThat((result as ParserBundleValidationResult.Invalid).reason)
            .isEqualTo(ParserBundleFailureReason.MALFORMED_JSON)
    }

    @Test
    fun validate_rejectsIncompatibleSchema() {
        val rules = ParserBundleTestFixtures.sampleRulesUtf8(schemaVersion = 1)
        // Envelope schema unsupported; still needs matching checksum/signature of rules
        val json = ParserBundleTestFixtures.signedBundleJson(
            rulesUtf8 = rules,
            schemaVersion = 99,
        )
        val result = manager.validateSignedBundle(json)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Invalid::class.java)
        assertThat((result as ParserBundleValidationResult.Invalid).reason)
            .isEqualTo(ParserBundleFailureReason.INCOMPATIBLE_SCHEMA)
    }

    @Test
    fun validate_rejectsIncompatibleAppVersion() {
        val json = ParserBundleTestFixtures.signedBundleJson(minimumAppVersion = "9.0.0")
        val result = manager.validateSignedBundle(json)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Invalid::class.java)
        assertThat((result as ParserBundleValidationResult.Invalid).reason)
            .isEqualTo(ParserBundleFailureReason.INCOMPATIBLE_APP_VERSION)
    }

    @Test
    fun validate_rejectsBadChecksum() {
        val rules = ParserBundleTestFixtures.sampleRulesUtf8()
        val json = ParserBundleTestFixtures.signedBundleJson(
            rulesUtf8 = rules,
            checksumOverride = "0".repeat(64),
            signatureOverride = ParserBundleTestFixtures.signRulesUtf8(rules),
        )
        val result = manager.validateSignedBundle(json)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Invalid::class.java)
        assertThat((result as ParserBundleValidationResult.Invalid).reason)
            .isEqualTo(ParserBundleFailureReason.CHECKSUM_MISMATCH)
    }

    @Test
    fun validate_rejectsTamperedRulesPayload() {
        val original = ParserBundleTestFixtures.sampleRulesUtf8(parserVersion = "test-2026.08.12.1")
        val tampered = ParserBundleTestFixtures.sampleRulesUtf8(parserVersion = "test-2026.08.12.1")
            .replace("Fixture Bank", "Evil Bank")
        val json = ParserBundleTestFixtures.signedBundleJson(
            rulesUtf8 = original,
            tamperRulesInEnvelope = tampered,
        )
        val result = manager.validateSignedBundle(json)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Invalid::class.java)
        val reason = (result as ParserBundleValidationResult.Invalid).reason
        assertThat(reason).isAnyOf(
            ParserBundleFailureReason.CHECKSUM_MISMATCH,
            ParserBundleFailureReason.SIGNATURE_INVALID,
        )
    }

    @Test
    fun validate_rejectsInvalidSignature() {
        val rules = ParserBundleTestFixtures.sampleRulesUtf8()
        val json = ParserBundleTestFixtures.signedBundleJson(
            rulesUtf8 = rules,
            signatureOverride = "MEUCIQDbadSignatureAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIgA=",
        )
        val result = manager.validateSignedBundle(json)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Invalid::class.java)
        assertThat((result as ParserBundleValidationResult.Invalid).reason)
            .isEqualTo(ParserBundleFailureReason.SIGNATURE_INVALID)
    }

    @Test
    fun validate_rejectsInvalidPatternShape() {
        val rules = ParserBundleTestFixtures.sampleRulesUtf8(amountGroup = "missing_group")
        val json = ParserBundleTestFixtures.signedBundleJson(
            rulesUtf8 = rules,
            parserVersion = "test-2026.08.12.1",
            rulesVersion = "test-rules-1",
        )
        val result = manager.validateSignedBundle(json)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Invalid::class.java)
        assertThat((result as ParserBundleValidationResult.Invalid).reason)
            .isEqualTo(ParserBundleFailureReason.VALIDATION_FAILED)
    }

    @Test
    fun validate_rejectsUnknownExecutableLookingField() {
        val bad = """
            {
              "schemaVersion": 1,
              "parserVersion": "x",
              "rulesVersion": "y",
              "minimumAppVersion": "0.1.0",
              "checksumSha256": "aa",
              "signatureBase64": "bb",
              "rulesUtf8": "{}",
              "evalHook": "alert(1)"
            }
        """.trimIndent()
        val result = manager.validateSignedBundle(bad)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Invalid::class.java)
        assertThat((result as ParserBundleValidationResult.Invalid).reason)
            .isEqualTo(ParserBundleFailureReason.MALFORMED_JSON)
    }

    @Test
    fun installAndActivate_verifiedBundle_succeeds() = runBlocking {
        val json = ParserBundleTestFixtures.signedBundleJson()
        val install = manager.installVerifiedBundle(json)
        assertThat(install).isInstanceOf(ParserBundleInstallResult.Installed::class.java)
        val version = (install as ParserBundleInstallResult.Installed).metadata.parserVersion
        val activated = manager.activateVerified(version)
        assertThat(activated).isInstanceOf(ParserBundleActivationResult.Activated::class.java)
        val ok = activated as ParserBundleActivationResult.Activated
        assertThat(ok.usedBundledFallback).isFalse()
        assertThat(ok.metadata.status).isEqualTo(ParserBundleStatus.ACTIVE)
        assertThat(manager.loadActiveRules()?.institutions).isNotEmpty()
    }

    @Test
    fun activate_fallsBackToBundled_whenInstalledRulesTampered() = runBlocking {
        val json = ParserBundleTestFixtures.signedBundleJson()
        val install = manager.installVerifiedBundle(json) as ParserBundleInstallResult.Installed
        val version = install.metadata.parserVersion

        // Corrupt on-disk rules after install (simulates tampering).
        repository.install(
            metadata = install.metadata,
            rulesDocument = ParserRulesDocument.of("""{"tampered":true}"""),
        )

        val activated = manager.activateVerified(version)
        assertThat(activated).isInstanceOf(ParserBundleActivationResult.Activated::class.java)
        val ok = activated as ParserBundleActivationResult.Activated
        assertThat(ok.usedBundledFallback).isTrue()
        assertThat(ok.metadata.parserVersion.value).startsWith("bundled-")
        assertThat(repository.findMetadata(version)?.status)
            .isEqualTo(ParserBundleStatus.INVALID)
    }

    @Test
    fun ensureActiveOrFallback_installsBundledWhenNothingActive() = runBlocking {
        val result = manager.ensureActiveOrFallbackToBundled()
        assertThat(result).isInstanceOf(ParserBundleActivationResult.Activated::class.java)
        val ok = result as ParserBundleActivationResult.Activated
        assertThat(ok.usedBundledFallback).isTrue()
        assertThat(ok.rules.parserVersion.value).startsWith("bundled-")
        assertThat(repository.findActiveMetadata()?.status).isEqualTo(ParserBundleStatus.ACTIVE)
    }

    @Test
    fun ensureActiveOrFallback_keepsValidActiveBundle() = runBlocking {
        val json = ParserBundleTestFixtures.signedBundleJson()
        val install = manager.installVerifiedBundle(json) as ParserBundleInstallResult.Installed
        manager.activateVerified(install.metadata.parserVersion)

        val result = manager.ensureActiveOrFallbackToBundled()
        assertThat(result).isInstanceOf(ParserBundleActivationResult.Activated::class.java)
        val ok = result as ParserBundleActivationResult.Activated
        assertThat(ok.usedBundledFallback).isFalse()
        assertThat(ok.metadata.parserVersion).isEqualTo(install.metadata.parserVersion)
    }

    @Test
    fun bundledDefaultAsset_verifiesWithEmbeddedPublicKey() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val assetLoader = ParserAssetLoader(context)
        val bundled = BundledParserRulesProvider(assetLoader, codec).loadSignedBundleJson()
        val result = manager.validateSignedBundle(bundled)
        assertThat(result).isInstanceOf(ParserBundleValidationResult.Valid::class.java)
    }

    @Test
    fun install_rejectsCorruptDownloadedPackage_withoutActivating() = runBlocking {
        // Pre-seed a good active bundled package
        manager.ensureActiveOrFallbackToBundled()
        val before = repository.findActiveMetadata()!!.parserVersion

        val rejected = manager.installVerifiedBundle("{")
        assertThat(rejected).isInstanceOf(ParserBundleInstallResult.Rejected::class.java)
        assertThat(repository.findActiveMetadata()?.parserVersion).isEqualTo(before)
    }
}
