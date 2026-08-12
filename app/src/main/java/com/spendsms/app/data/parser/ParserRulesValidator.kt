package com.spendsms.app.data.parser

import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.InstitutionRuleSet
import com.spendsms.app.domain.parsing.ParserSchema
import com.spendsms.app.domain.parsing.SignedParserBundle
import com.spendsms.app.domain.parsing.TransactionPatternRule
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structural validation of declarative parser rules (no SMS matching).
 */
@Singleton
class ParserRulesValidator @Inject constructor() {

    fun validateRulesDocument(
        rules: DeclarativeParserRules,
        expectedParserVersion: String? = null,
        expectedRulesVersion: String? = null,
    ): ValidationOutcome {
        if (rules.schemaVersion !in ParserSchema.SUPPORTED_VERSIONS) {
            return ValidationOutcome.Failure(
                code = FailureCode.INCOMPATIBLE_SCHEMA,
                detail = "Unsupported rules schemaVersion=${rules.schemaVersion}",
            )
        }
        if (expectedParserVersion != null && rules.parserVersion.value != expectedParserVersion) {
            return ValidationOutcome.Failure(
                code = FailureCode.VALIDATION_FAILED,
                detail = "rules.parserVersion does not match envelope",
            )
        }
        if (expectedRulesVersion != null && rules.rulesVersion.value != expectedRulesVersion) {
            return ValidationOutcome.Failure(
                code = FailureCode.VALIDATION_FAILED,
                detail = "rules.rulesVersion does not match envelope",
            )
        }

        val patternIds = mutableSetOf<String>()
        for (institution in rules.institutions) {
            val institutionFailure = validateInstitution(institution, patternIds)
            if (institutionFailure != null) return institutionFailure
        }
        return ValidationOutcome.Success
    }

    fun validateChecksum(rulesUtf8: String, expectedSha256Hex: String): ValidationOutcome {
        val actual = sha256Hex(rulesUtf8.toByteArray(Charsets.UTF_8))
        return if (actual.equals(expectedSha256Hex.trim(), ignoreCase = true)) {
            ValidationOutcome.Success
        } else {
            ValidationOutcome.Failure(
                code = FailureCode.CHECKSUM_MISMATCH,
                detail = "SHA-256 checksum mismatch",
            )
        }
    }

    fun validateEnvelopeVersions(bundle: SignedParserBundle): ValidationOutcome {
        if (bundle.schemaVersion !in ParserSchema.SUPPORTED_VERSIONS) {
            return ValidationOutcome.Failure(
                code = FailureCode.INCOMPATIBLE_SCHEMA,
                detail = "Unsupported envelope schemaVersion=${bundle.schemaVersion}",
            )
        }
        return ValidationOutcome.Success
    }

    private fun validateInstitution(
        institution: InstitutionRuleSet,
        patternIds: MutableSet<String>,
    ): ValidationOutcome.Failure? {
        for (pattern in institution.transactionPatterns) {
            val failure = validatePattern(pattern, patternIds) ?: continue
            return failure
        }
        return null
    }

    private fun validatePattern(
        pattern: TransactionPatternRule,
        patternIds: MutableSet<String>,
    ): ValidationOutcome.Failure? {
        if (!patternIds.add(pattern.id)) {
            return ValidationOutcome.Failure(
                code = FailureCode.VALIDATION_FAILED,
                detail = "Duplicate pattern id: ${pattern.id}",
            )
        }
        if (FORBIDDEN_TOKEN_REGEX.containsMatchIn(pattern.regex)) {
            return ValidationOutcome.Failure(
                code = FailureCode.VALIDATION_FAILED,
                detail = "Pattern ${pattern.id} contains disallowed executable-looking tokens",
            )
        }
        try {
            Pattern.compile(pattern.regex)
        } catch (e: PatternSyntaxException) {
            return ValidationOutcome.Failure(
                code = FailureCode.VALIDATION_FAILED,
                detail = "Pattern ${pattern.id} has invalid regex: ${e.description}",
            )
        }
        val namedGroups = NAMED_GROUP_DECL.findAll(pattern.regex).map { it.groupValues[1] }.toSet()
        val required = listOfNotNull(
            pattern.amountGroup,
            pattern.merchantGroup,
            pattern.currencyGroup,
            pattern.referenceGroup,
            pattern.dateGroup,
        )
        for (group in required) {
            if (group !in namedGroups) {
                return ValidationOutcome.Failure(
                    code = FailureCode.VALIDATION_FAILED,
                    detail = "Pattern ${pattern.id} missing named group '$group'",
                )
            }
        }
        return null
    }

    sealed class ValidationOutcome {
        data object Success : ValidationOutcome()
        data class Failure(val code: FailureCode, val detail: String) : ValidationOutcome()
    }

    enum class FailureCode {
        INCOMPATIBLE_SCHEMA,
        CHECKSUM_MISMATCH,
        VALIDATION_FAILED,
    }

    companion object {
        private val NAMED_GROUP_DECL = Regex("""\(\?<([A-Za-z][A-Za-z0-9]*)>""")

        /**
         * Soft guard against embedding script/code payloads inside "regex" strings.
         * Declarative packages must remain data-only.
         */
        private val FORBIDDEN_TOKEN_REGEX = Regex(
            """(?i)\b(javascript:|eval\s*\(|Function\s*\(|Runtime\.getRuntime|ProcessBuilder|dalvik|dexClassLoader)\b""",
        )

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { b -> "%02x".format(b) }
        }
    }
}
