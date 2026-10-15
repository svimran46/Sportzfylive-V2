package com.sportzfy.live.testing

import com.sportzfy.live.domain.model.ClockFormat
import com.sportzfy.live.domain.model.UserSettings
import com.sportzfy.live.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory settings; [current] reads what was last written. */
class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<UserSettings> = state

    val current: UserSettings get() = state.value

    fun emit(settings: UserSettings) {
        state.value = settings
    }

    override suspend fun setPreferredLanguage(language: String?) {
        state.value = state.value.copy(preferredLanguage = language)
    }

    override suspend fun setClockFormat(format: ClockFormat) {
        state.value = state.value.copy(clockFormat = format)
    }
}
