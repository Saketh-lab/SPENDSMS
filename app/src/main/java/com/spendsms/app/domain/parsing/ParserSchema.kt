package com.spendsms.app.domain.parsing

/**
 * Supported declarative parser-rule schema versions for Phase-0.
 */
object ParserSchema {
    const val CURRENT_VERSION: Int = 1
    val SUPPORTED_VERSIONS: Set<Int> = setOf(CURRENT_VERSION)
}
