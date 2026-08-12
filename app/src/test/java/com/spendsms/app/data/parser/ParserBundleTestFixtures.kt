package com.spendsms.app.data.parser

import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.InstitutionRuleSet
import com.spendsms.app.domain.parsing.SignedParserBundle
import com.spendsms.app.domain.parsing.TransactionPatternRule
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.json.Json

/**
 * Test helper: builds signed parser envelopes with the Phase-0 fixture private key.
 */
object ParserBundleTestFixtures {

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

    private val codec = ParserRulesCodec(json)

    fun loadPrivateKeyPem(): ByteArray {
        val pem = checkNotNull(
            javaClass.classLoader!!.getResourceAsStream("parser/test_signing_private.pem"),
        ).bufferedReader().readText()
        val b64 = pem
            .lines()
            .filter { !it.startsWith("-----") && it.isNotBlank() }
            .joinToString("")
        return Base64.getDecoder().decode(b64)
    }

    fun signRulesUtf8(rulesUtf8: String): String {
        val key = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(loadPrivateKeyPem()))
        val signature = Signature.getInstance(ParserBundleSignatureVerifier.ALGORITHM)
        signature.initSign(key)
        signature.update(rulesUtf8.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    fun checksum(rulesUtf8: String): String =
        ParserRulesValidator.sha256Hex(rulesUtf8.toByteArray(Charsets.UTF_8))

    fun sampleRules(
        parserVersion: String = "test-2026.08.12.1",
        rulesVersion: String = "test-rules-1",
        schemaVersion: Int = 1,
        regex: String = """Rs\.?\s?(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)""",
        amountGroup: String = "amount",
        patternId: String = "fixture_debit_v1",
    ): DeclarativeParserRules = DeclarativeParserRules(
        schemaVersion = schemaVersion,
        parserVersion = ParserVersion.of(parserVersion),
        rulesVersion = RulesVersion.of(rulesVersion),
        institutions = listOf(
            InstitutionRuleSet(
                institution = "Fixture Bank",
                senderPatterns = listOf("FX-BANK"),
                transactionPatterns = listOf(
                    TransactionPatternRule(
                        id = patternId,
                        type = TransactionDirection.DEBIT,
                        regex = regex,
                        amountGroup = amountGroup,
                    ),
                ),
            ),
        ),
    )

    fun sampleRulesUtf8(
        parserVersion: String = "test-2026.08.12.1",
        rulesVersion: String = "test-rules-1",
        schemaVersion: Int = 1,
        regex: String = """Rs\.?\s?(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)""",
        amountGroup: String = "amount",
    ): String = codec.encodeRules(
        sampleRules(
            parserVersion = parserVersion,
            rulesVersion = rulesVersion,
            schemaVersion = schemaVersion,
            regex = regex,
            amountGroup = amountGroup,
        ),
    )

    fun signedBundleJson(
        rulesUtf8: String = sampleRulesUtf8(),
        parserVersion: String = "test-2026.08.12.1",
        rulesVersion: String = "test-rules-1",
        minimumAppVersion: String = "0.1.0",
        schemaVersion: Int = 1,
        checksumOverride: String? = null,
        signatureOverride: String? = null,
        tamperRulesInEnvelope: String? = null,
    ): String {
        val payload = tamperRulesInEnvelope ?: rulesUtf8
        val bundle = SignedParserBundle(
            schemaVersion = schemaVersion,
            parserVersion = ParserVersion.of(parserVersion),
            rulesVersion = RulesVersion.of(rulesVersion),
            minimumAppVersion = minimumAppVersion,
            checksumSha256 = checksumOverride ?: checksum(rulesUtf8),
            signatureBase64 = signatureOverride ?: signRulesUtf8(rulesUtf8),
            rulesUtf8 = payload,
        )
        return codec.encodeSignedBundle(bundle)
    }

    fun codec(): ParserRulesCodec = codec
}
