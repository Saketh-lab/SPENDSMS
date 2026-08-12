package com.spendsms.app.domain.parsing

import java.security.MessageDigest

/**
 * One-way hashes for idempotency / reference tokens. Never reverse to SMS body.
 */
object ParsingHashes {

    fun sourceMessageHash(sourceMessageId: String, sender: String, receivedAtEpochMillis: Long): String =
        sha256Hex("$sourceMessageId|$sender|$receivedAtEpochMillis")

    fun referenceHash(rawReference: String): String =
        sha256Hex(rawReference.trim()).take(32)

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
