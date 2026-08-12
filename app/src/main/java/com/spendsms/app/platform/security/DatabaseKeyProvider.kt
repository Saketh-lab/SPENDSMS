package com.spendsms.app.platform.security

import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the SQLCipher passphrase using a [SecurePassphraseStore]
 * (Keystore-backed in production — Steps 2–3).
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    private val store: SecurePassphraseStore,
) {

    fun getOrCreatePassphrase(): ByteArray {
        val existing = store.read(PREF_PASSPHRASE)
        if (existing != null) return existing
        val generated = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        store.write(PREF_PASSPHRASE, generated)
        return generated
    }

    fun hasStoredPassphrase(): Boolean = store.contains(PREF_PASSPHRASE)

    companion object {
        const val PREF_PASSPHRASE = "db_passphrase_v1"
        private const val PASSPHRASE_BYTES = 32
    }
}
