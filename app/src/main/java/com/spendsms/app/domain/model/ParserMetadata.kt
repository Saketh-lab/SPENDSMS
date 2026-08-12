package com.spendsms.app.domain.model

/**
 * Installed parser-rule package metadata (Step-3 `parser_metadata`).
 *
 * Exactly one ACTIVE bundle is an application-level invariant enforced later.
 */
data class ParserMetadata(
    val parserVersion: ParserVersion,
    val rulesVersion: RulesVersion,
    val schemaVersion: Int,
    val checksum: String,
    val installedAt: EpochMillis,
    val activatedAt: EpochMillis?,
    val status: ParserBundleStatus,
) {
    init {
        require(schemaVersion >= 1) {
            "schemaVersion must be >= 1, was $schemaVersion"
        }
        require(checksum.isNotBlank()) { "checksum must not be blank" }
        when (status) {
            ParserBundleStatus.ACTIVE -> {
                require(activatedAt != null) {
                    "activatedAt is required when status is ACTIVE"
                }
                require(activatedAt >= installedAt) {
                    "activatedAt must be >= installedAt"
                }
            }
            ParserBundleStatus.INSTALLED,
            ParserBundleStatus.ROLLBACK,
            ParserBundleStatus.INVALID,
            -> Unit
        }
    }
}
