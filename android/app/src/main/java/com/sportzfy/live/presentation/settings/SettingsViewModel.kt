package com.sportzfy.live.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.domain.model.ClockFormat
import com.sportzfy.live.domain.model.UserSettings
import com.sportzfy.live.domain.repository.FavoritesRepository
import com.sportzfy.live.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val savedCount: Int = 0,
)

/** Playback and display preferences plus the saved-matches count. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        favoritesRepository.favoriteIds.onStart { emit(emptySet()) },
    ) { settings, saved ->
        SettingsUiState(settings = settings, savedCount = saved.size)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /** null means any language. */
    fun setPreferredLanguage(language: String?) {
        viewModelScope.launch { settingsRepository.setPreferredLanguage(language) }
    }

    fun setClockFormat(format: ClockFormat) {
        viewModelScope.launch { settingsRepository.setClockFormat(format) }
    }

    fun clearSavedMatches() {
        viewModelScope.launch { favoritesRepository.clear() }
    }
}
