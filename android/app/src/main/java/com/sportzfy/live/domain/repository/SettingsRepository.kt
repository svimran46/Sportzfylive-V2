package com.sportzfy.live.domain.repository

import com.sportzfy.live.domain.model.ClockFormat
import com.sportzfy.live.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    /** null means any language. */
    suspend fun setPreferredLanguage(language: String?)

    suspend fun setClockFormat(format: ClockFormat)
}
