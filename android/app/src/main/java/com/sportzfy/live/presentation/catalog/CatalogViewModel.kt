package com.sportzfy.live.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.domain.repository.AppConfigRepository
import com.sportzfy.live.domain.repository.FavoritesRepository
import com.sportzfy.live.domain.repository.MatchRepository
import com.sportzfy.live.presentation.model.CatalogUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Search, sport filter and Saved filter over the shared match feed. Every
 * input is combined into one [CatalogUiState], so a keystroke, a pill tap or
 * a feed refresh all re-filter in a single pass.
 */
class CatalogViewModel(
    private val matchRepository: MatchRepository,
    private val favoritesRepository: FavoritesRepository,
    private val configRepository: AppConfigRepository,
) : ViewModel() {

    private data class Filters(
        val category: SportCategory? = null,
        val favoritesOnly: Boolean = false,
        val query: String = "",
    )

    private val filters = MutableStateFlow(Filters())

    val uiState: StateFlow<CatalogUiState> =
        combine(
            matchRepository.feed,
            filters,
            favoritesRepository.favoriteIds.onStart { emit(emptySet()) },
            configRepository.config,
        ) { feed, f, favorites, config ->
            CatalogUiState.from(feed, f.category, f.query, favorites, f.favoritesOnly, config)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CatalogUiState.from(matchRepository.feed.value, null, "", config = configRepository.config.value),
        )

    /** Cheap to call often: the repository serves from its 60s cache. */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch { matchRepository.refresh(force) }
    }

    fun selectCategory(value: SportCategory?) {
        filters.value = filters.value.copy(category = value, favoritesOnly = false)
    }

    fun showFavorites() {
        filters.value = filters.value.copy(category = null, favoritesOnly = true)
    }

    fun setQuery(value: String) {
        filters.value = filters.value.copy(query = value)
    }

    fun toggleFavorite(matchId: String) {
        viewModelScope.launch { favoritesRepository.toggle(matchId) }
    }
}
