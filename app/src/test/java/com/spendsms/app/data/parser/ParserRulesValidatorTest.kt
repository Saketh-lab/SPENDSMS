package com.spendsms.app.data.parser

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.InstitutionRuleSet
import com.spendsms.app.domain.parsing.TransactionPatternRule
import org.junit.Test

class ParserRulesValidatorTest {

    private val validator = ParserRulesValidator()

    @Test
    fun acceptsWellFormedRules() {
        val rules = ParserBundleTestFixtures.sampleRules()
        assertThat(validator.validateRulesDocument(rules))
            .isEqualTo(ParserRulesValidator.ValidationOutcome.Success)
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val rules = ParserBundleTestFixtures.sampleRules(schemaVersion = 1).let {
            // Bypass domain require by encoding/decoding with dto mutation is hard;
            // construct via copy after hacking — use reflection-free approach:
            DeclarativeParserRules(
                schemaVersion = 2,
                parserVersion = it.parserVersion,
                rulesVersion = it.rulesVersion,
                institutions = it.institutions,
            )
        }
        val outcome = validator.validateRulesDocument(rules)
        assertThat(outcome).isInstanceOf(ParserRulesValidator.ValidationOutcome.Failure::class.java)
        assertThat((outcome as ParserRulesValidator.ValidationOutcome.Failure).code)
            .isEqualTo(ParserRulesValidator.FailureCode.INCOMPATIBLE_SCHEMA)
    }

    @Test
    fun rejectsMissingNamedGroup() {
        val rules = ParserBundleTestFixtures.sampleRules(amountGroup = "nope")
        val outcome = validator.validateRulesDocument(rules)
        assertThat(outcome).isInstanceOf(ParserRulesValidator.ValidationOutcome.Failure::class.java)
    }

    @Test
    fun rejectsInvalidRegex() {
        val rules = ParserBundleTestFixtures.sampleRules(regex = "(unclosed")
        val outcome = validator.validateRulesDocument(rules)
        assertThat(outcome).isInstanceOf(ParserRulesValidator.ValidationOutcome.Failure::class.java)
    }

    @Test
    fun checksumMatchesKnownDigest() {
        val text = "hello"
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertThat(validator.validateChecksum(text, expected))
            .isEqualTo(ParserRulesValidator.ValidationOutcome.Success)
        assertThat(validator.validateChecksum(text, "deadbeef"))
            .isInstanceOf(ParserRulesValidator.ValidationOutcome.Failure::class.java)
    }

    @Test
    fun rejectsDuplicatePatternIds() {
        val pattern = TransactionPatternRule(
            id = "dup",
            type = TransactionDirection.DEBIT,
            regex = "(?<amount>\\d+)",
            amountGroup = "amount",
        )
        val rules = DeclarativeParserRules(
            schemaVersion = 1,
            parserVersion = ParserVersion.of("v"),
            rulesVersion = RulesVersion.of("r"),
            institutions = listOf(
                InstitutionRuleSet(
                    institution = "A",
                    senderPatterns = listOf("A"),
                    transactionPatterns = listOf(pattern, pattern.copy()),
                ),
            ),
        )
        val outcome = validator.validateRulesDocument(rules)
        assertThat(outcome).isInstanceOf(ParserRulesValidator.ValidationOutcome.Failure::class.java)
    }
}

class AppVersionCompatibilityTest {
    private val compat = AppVersionCompatibility()

    @Test
    fun comparesCoreSemverIgnoringPrerelease() {
        assertThat(compat.isCompatible("0.1.0-phase0", "0.1.0")).isTrue()
        assertThat(compat.isCompatible("0.1.0", "0.2.0")).isFalse()
        assertThat(compat.isCompatible("1.0.0", "0.9.9")).isTrue()
    }
}

class ParserRulesCodecTest {
    private val codec = ParserBundleTestFixtures.codec()

    @Test
    fun roundTripsRules() {
        val original = ParserBundleTestFixtures.sampleRules()
        val encoded = codec.encodeRules(original)
        val decoded = codec.decodeRules(encoded)
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun rejectsUnknownKeysInRules() {
        val rules = ParserBundleTestFixtures.sampleRulesUtf8()
        val bad = rules.dropLast(1) + """, "bytecode": "nope"}"""
        try {
            codec.decodeRules(bad)
            throw AssertionError("expected decode failure for unknown key")
        } catch (_: ParserRulesCodecException) {
            // expected
        }
    }
}
