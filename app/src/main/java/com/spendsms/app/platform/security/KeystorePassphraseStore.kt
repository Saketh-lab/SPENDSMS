package com.spendsms.app.platform.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed passphrase store via [MasterKeys] + [EncryptedSharedPreferences].
 */
@Singleton
class KeystorePassphraseStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecurePassphraseStore {

    private val prefs: EncryptedSharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as EncryptedSharedPreferences
    }

    override fun read(key: String): ByteArray? {
        val encoded = prefs.getString(key, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    override fun write(key: String, value: ByteArray) {
        prefs.edit()
            .putString(key, Base64.encodeToString(value, Base64.NO_WRAP))
            .commit()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    companion object {
        const val PREFS_FILE = "spendsms_db_keystore"
    }
}
