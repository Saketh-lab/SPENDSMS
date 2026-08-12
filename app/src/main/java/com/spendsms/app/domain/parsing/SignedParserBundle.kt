package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.RulesVersion

/**
 * Signed parser-rule package envelope (manifest + declarative rules payload).
 *
 * [rulesUtf8] is the exact UTF-8 JSON of [DeclarativeParserRules] that was
 * checksummed and signed. Treat it as opaque bytes for verification.
 */
data class SignedParserBundle(
    val schemaVersion: Int,
    val parserVersion: ParserVersion,
    val rulesVersion: RulesVersion,
    val minimumAppVersion: String,
    val checksumSha256: String,
    val signatureBase64: String,
    val rulesUtf8: String,
) {
    init {
        require(schemaVersion >= 1) { "schemaVersion must be >= 1" }
        require(minimumAppVersion.isNotBlank()) { "minimumAppVersion must not be blank" }
        require(checksumSha256.isNotBlank()) { "checksumSha256 must not be blank" }
        require(signatureBase64.isNotBlank()) { "signatureBase64 must not be blank" }
        require(rulesUtf8.isNotBlank()) { "rulesUtf8 must not be blank" }
    }
}
