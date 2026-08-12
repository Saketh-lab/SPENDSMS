package com.spendsms.app.platform.security

/**
 * Abstraction over Keystore-backed secret storage so unit tests can substitute
 * an in-memory store when the Robolectric Keystore is unavailable.
 */
interface SecurePassphraseStore {
    fun read(key: String): ByteArray?
    fun write(key: String, value: ByteArray)
    fun contains(key: String): Boolean
}
