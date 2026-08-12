package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.model.TransactionDirection

/**
 * Declarative parser-rule document (data only — never executable code).
 *
 * Matches the Step-2 institution / senderPatterns / transactionPatterns shape,
 * wrapped with version metadata required by Step-3 parser_metadata.
 */
data class DeclarativeParserRules(
    val schemaVersion: Int,
    val parserVersion: ParserVersion,
    val rulesVersion: RulesVersion,
    val institutions: List<InstitutionRuleSet>,
) {
    init {
        require(schemaVersion >= 1) { "schemaVersion must be >= 1" }
        require(institutions.isNotEmpty()) { "institutions must not be empty" }
    }
}

data class InstitutionRuleSet(
    val institution: String,
    val senderPatterns: List<String>,
    val transactionPatterns: List<TransactionPatternRule>,
) {
    init {
        require(institution.isNotBlank()) { "institution must not be blank" }
        require(senderPatterns.isNotEmpty()) { "senderPatterns must not be empty" }
        require(senderPatterns.all { it.isNotBlank() }) { "senderPatterns must not contain blanks" }
        require(transactionPatterns.isNotEmpty()) { "transactionPatterns must not be empty" }
    }
}

/**
 * One declarative SMS pattern. Capture groups are named references only —
 * evaluated by [DeclarativeTransactionParser] as data, never as executable code.
 */
data class TransactionPatternRule(
    val id: String,
    val type: TransactionDirection,
    val regex: String,
    val amountGroup: String,
    val merchantGroup: String? = null,
    val currencyGroup: String? = null,
    val referenceGroup: String? = null,
    val dateGroup: String? = null,
) {
    init {
        require(id.isNotBlank()) { "pattern id must not be blank" }
        require(regex.isNotBlank()) { "regex must not be blank" }
        require(amountGroup.isNotBlank()) { "amountGroup must not be blank" }
    }
}
