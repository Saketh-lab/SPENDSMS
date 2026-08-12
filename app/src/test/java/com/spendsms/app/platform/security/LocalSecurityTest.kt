package com.spendsms.app.platform.security

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.data.room.SpendSmsDatabase
import com.spendsms.app.data.room.entity.TransactionEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalSecurityTest {

    @Test
    fun databaseFile_isUnderNoBackupDirectory() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = SpendSmsDatabase.databaseFile(context)
        assertThat(file.absolutePath).startsWith(context.noBackupFilesDir.absolutePath)
        assertThat(file.name).isEqualTo(SpendSmsDatabase.NAME)
    }

    @Test
    fun databaseKeyProvider_persistsStablePassphrase() {
        val store = InMemorySecurePassphraseStore()
        val provider = DatabaseKeyProvider(store)
        val first = provider.getOrCreatePassphrase()
        val second = provider.getOrCreatePassphrase()
        assertThat(first).isEqualTo(second)
        assertThat(first.size).isEqualTo(32)
        assertThat(provider.hasStoredPassphrase()).isTrue()
        assertThat(store.contains(DatabaseKeyProvider.PREF_PASSPHRASE)).isTrue()
    }

    @Test
    fun keystorePassphraseStore_usesDedicatedEncryptedPrefsFile() {
        assertThat(KeystorePassphraseStore.PREFS_FILE).isEqualTo("spendsms_db_keystore")
    }

    @Test
    fun transactionEntity_hasNoRawSmsBodyColumn() {
        val fields = TransactionEntity::class.java.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("body")
        assertThat(fields).doesNotContain("smsBody")
        assertThat(fields).doesNotContain("rawSms")
        assertThat(fields).contains("sourceMessageHash")
    }

    private class InMemorySecurePassphraseStore : SecurePassphraseStore {
        private val values = mutableMapOf<String, ByteArray>()

        override fun read(key: String): ByteArray? = values[key]?.copyOf()

        override fun write(key: String, value: ByteArray) {
            values[key] = value.copyOf()
        }

        override fun contains(key: String): Boolean = values.containsKey(key)
    }
}
