package com.spendsms.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spendsms_user_prefs",
)

/**
 * Local UI preferences only (onboarding + last analysis period). No SMS bodies.
 */
@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.userPreferencesDataStore

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] == true
    }

    val lastAnalysisPeriod: Flow<AnalysisPeriod?> = dataStore.data.map { prefs ->
        val start = prefs[KEY_PERIOD_START] ?: return@map null
        val end = prefs[KEY_PERIOD_END] ?: return@map null
        if (start > end) return@map null
        AnalysisPeriod(EpochMillis.of(start), EpochMillis.of(end))
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setLastAnalysisPeriod(period: AnalysisPeriod) {
        dataStore.edit {
            it[KEY_PERIOD_START] = period.start.toEpochMillis
            it[KEY_PERIOD_END] = period.end.toEpochMillis
        }
    }

    suspend fun clearAnalysisPeriod() {
        dataStore.edit {
            it.remove(KEY_PERIOD_START)
            it.remove(KEY_PERIOD_END)
        }
    }

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_PERIOD_START = longPreferencesKey("last_period_start")
        private val KEY_PERIOD_END = longPreferencesKey("last_period_end")
    }
}
