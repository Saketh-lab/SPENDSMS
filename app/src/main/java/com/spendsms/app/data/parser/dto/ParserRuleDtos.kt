package com.spendsms.app.data.parser.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeclarativeParserRulesDto(
    val schemaVersion: Int,
    val parserVersion: String,
    val rulesVersion: String,
    val institutions: List<InstitutionRuleSetDto>,
)

@Serializable
data class InstitutionRuleSetDto(
    val institution: String,
    val senderPatterns: List<String>,
    val transactionPatterns: List<TransactionPatternRuleDto>,
)

@Serializable
data class TransactionPatternRuleDto(
    val id: String,
    val type: String,
    val regex: String,
    val amountGroup: String,
    val merchantGroup: String? = null,
    val currencyGroup: String? = null,
    val referenceGroup: String? = null,
    val dateGroup: String? = null,
)

@Serializable
data class SignedParserBundleDto(
    val schemaVersion: Int,
    val parserVersion: String,
    val rulesVersion: String,
    val minimumAppVersion: String,
    val checksumSha256: String,
    val signatureBase64: String,
    val rulesUtf8: String,
)
