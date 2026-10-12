package com.sportzfy.live.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sportzfy.live.domain.model.ClockFormat
import com.sportzfy.live.domain.model.UserSettings
import com.sportzfy.live.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        .map { prefs ->
            UserSettings(
                preferredLanguage = when (val stored = prefs[LANGUAGE]) {
                    null -> UserSettings.DEFAULT_LANGUAGE
                    ANY_LANGUAGE -> null
                    else -> stored
                },
                clockFormat = ClockFormat.entries.firstOrNull { it.name == prefs[CLOCK] } ?: ClockFormat.SYSTEM,
            )
        }
        .distinctUntilChanged()

    override suspend fun setPreferredLanguage(language: String?) {
        dataStore.edit { it[LANGUAGE] = language ?: ANY_LANGUAGE }
    }

    override suspend fun setClockFormat(format: ClockFormat) {
        dataStore.edit { it[CLOCK] = format.name }
    }

    private companion object {
        val LANGUAGE = stringPreferencesKey("preferred_language")
        val CLOCK = stringPreferencesKey("clock_format")

        /** Stored for "any", so it isn't confused with "never set" (English). */
        const val ANY_LANGUAGE = "*"
    }
}
