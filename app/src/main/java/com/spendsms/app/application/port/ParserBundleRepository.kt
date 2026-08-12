package com.spendsms.app.application.port

import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion

/**
 * Opaque declarative parser-rule document (JSON/data only — never executable code).
 */
@JvmInline
value class ParserRulesDocument private constructor(val utf8Json: String) {
    init {
        require(utf8Json.isNotBlank()) { "ParserRulesDocument must not be blank" }
    }

    companion object {
        fun of(utf8Json: String): ParserRulesDocument = ParserRulesDocument(utf8Json)
    }
}

/**
 * Installed/active parser-rule packages and metadata (Step-3 Parser Bundle Manager).
 */
interface ParserBundleRepository {

    suspend fun findActiveMetadata(): ParserMetadata?

    suspend fun findMetadata(version: ParserVersion): ParserMetadata?

    suspend fun listInstalled(): List<ParserMetadata>

    /**
     * Persist metadata + declarative rules together. Activation is a separate call
     * so installs can be validated before becoming ACTIVE.
     */
    suspend fun install(
        metadata: ParserMetadata,
        rulesDocument: ParserRulesDocument,
    )

    suspend fun activate(version: ParserVersion)

    suspend fun markRollback(version: ParserVersion)

    suspend fun markInvalid(version: ParserVersion)

    suspend fun loadActiveRulesDocument(): ParserRulesDocument?

    suspend fun loadRulesDocument(version: ParserVersion): ParserRulesDocument?
}
