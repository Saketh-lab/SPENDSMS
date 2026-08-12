package com.spendsms.app.data.parser

import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.parsing.DeclarativeParserRules
import com.spendsms.app.domain.parsing.InstitutionRuleSet
import com.spendsms.app.domain.parsing.SignedParserBundle
import com.spendsms.app.domain.parsing.TransactionPatternRule
import com.spendsms.app.data.parser.dto.DeclarativeParserRulesDto
import com.spendsms.app.data.parser.dto.InstitutionRuleSetDto
import com.spendsms.app.data.parser.dto.SignedParserBundleDto
import com.spendsms.app.data.parser.dto.TransactionPatternRuleDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * JSON encode/decode for declarative parser rules and signed envelopes.
 *
 * Unknown keys are rejected (data-only contract; no extension hooks for code).
 */
@Singleton
class ParserRulesCodec @Inject constructor(
    private val json: Json,
) {

    fun decodeSignedBundle(utf8: String): SignedParserBundle {
        val dto = decodeDto<SignedParserBundleDto>(utf8)
        return SignedParserBundle(
            schemaVersion = dto.schemaVersion,
            parserVersion = ParserVersion.of(dto.parserVersion),
            rulesVersion = RulesVersion.of(dto.rulesVersion),
            minimumAppVersion = dto.minimumAppVersion.trim(),
            checksumSha256 = dto.checksumSha256.trim().lowercase(),
            signatureBase64 = dto.signatureBase64.trim(),
            rulesUtf8 = dto.rulesUtf8,
        )
    }

    fun encodeSignedBundle(bundle: SignedParserBundle): String {
        val dto = SignedParserBundleDto(
            schemaVersion = bundle.schemaVersion,
            parserVersion = bundle.parserVersion.value,
            rulesVersion = bundle.rulesVersion.value,
            minimumAppVersion = bundle.minimumAppVersion,
            checksumSha256 = bundle.checksumSha256,
            signatureBase64 = bundle.signatureBase64,
            rulesUtf8 = bundle.rulesUtf8,
        )
        return json.encodeToString(SignedParserBundleDto.serializer(), dto)
    }

    fun decodeRules(utf8: String): DeclarativeParserRules {
        val dto = decodeDto<DeclarativeParserRulesDto>(utf8)
        return dto.toDomain()
    }

    fun encodeRules(rules: DeclarativeParserRules): String {
        val dto = rules.toDto()
        return json.encodeToString(DeclarativeParserRulesDto.serializer(), dto)
    }

    private inline fun <reified T> decodeDto(utf8: String): T {
        try {
            return json.decodeFromString(utf8)
        } catch (e: SerializationException) {
            throw ParserRulesCodecException("Malformed or schema-invalid JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ParserRulesCodecException("Invalid parser-rule values: ${e.message}", e)
        }
    }
}

class ParserRulesCodecException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun DeclarativeParserRulesDto.toDomain(): DeclarativeParserRules =
    DeclarativeParserRules(
        schemaVersion = schemaVersion,
        parserVersion = ParserVersion.of(parserVersion),
        rulesVersion = RulesVersion.of(rulesVersion),
        institutions = institutions.map { it.toDomain() },
    )

internal fun InstitutionRuleSetDto.toDomain(): InstitutionRuleSet =
    InstitutionRuleSet(
        institution = institution,
        senderPatterns = senderPatterns,
        transactionPatterns = transactionPatterns.map { it.toDomain() },
    )

internal fun TransactionPatternRuleDto.toDomain(): TransactionPatternRule {
    val direction = try {
        TransactionDirection.valueOf(type.trim().uppercase())
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Unknown transaction pattern type: $type")
    }
    return TransactionPatternRule(
        id = id,
        type = direction,
        regex = regex,
        amountGroup = amountGroup,
        merchantGroup = merchantGroup,
        currencyGroup = currencyGroup,
        referenceGroup = referenceGroup,
        dateGroup = dateGroup,
    )
}

internal fun DeclarativeParserRules.toDto(): DeclarativeParserRulesDto =
    DeclarativeParserRulesDto(
        schemaVersion = schemaVersion,
        parserVersion = parserVersion.value,
        rulesVersion = rulesVersion.value,
        institutions = institutions.map { it.toDto() },
    )

internal fun InstitutionRuleSet.toDto(): InstitutionRuleSetDto =
    InstitutionRuleSetDto(
        institution = institution,
        senderPatterns = senderPatterns,
        transactionPatterns = transactionPatterns.map { it.toDto() },
    )

internal fun TransactionPatternRule.toDto(): TransactionPatternRuleDto =
    TransactionPatternRuleDto(
        id = id,
        type = type.name,
        regex = regex,
        amountGroup = amountGroup,
        merchantGroup = merchantGroup,
        currencyGroup = currencyGroup,
        referenceGroup = referenceGroup,
        dateGroup = dateGroup,
    )
