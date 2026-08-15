package com.spendsms.app.data.parser.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spendsms.app.application.port.parser.ParserUpdateStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.parserUpdateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spendsms_parser_update",
)

@Singleton
class DataStoreParserUpdateStateStore @Inject constructor(
    @ApplicationContext context: Context,
) : ParserUpdateStateStore {

    private val dataStore = context.parserUpdateDataStore

    override suspend fun lastManifestEtag(): String? =
        dataStore.data.first()[KEY_ETAG]

    override suspend fun lastCheckEpochMillis(): Long? =
        dataStore.data.first()[KEY_LAST_CHECK]

    override suspend fun highestAcceptedParserVersion(): String? =
        dataStore.data.first()[KEY_HIGHEST_VERSION]

    override suspend fun recordManifestCheck(
        etag: String?,
        checkedAtEpochMillis: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CHECK] = checkedAtEpochMillis
            if (etag != null) {
                prefs[KEY_ETAG] = etag
            }
        }
    }

    override suspend fun recordSuccessfulActivation(
        parserVersion: String,
        etag: String?,
        activatedAtEpochMillis: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CHECK] = activatedAtEpochMillis
            prefs[KEY_HIGHEST_VERSION] = parserVersion
            if (etag != null) {
                prefs[KEY_ETAG] = etag
            }
        }
    }

    companion object {
        private val KEY_ETAG = stringPreferencesKey("manifest_etag")
        private val KEY_LAST_CHECK = longPreferencesKey("last_check_epoch_ms")
        private val KEY_HIGHEST_VERSION = stringPreferencesKey("highest_accepted_parser_version")
    }
}
