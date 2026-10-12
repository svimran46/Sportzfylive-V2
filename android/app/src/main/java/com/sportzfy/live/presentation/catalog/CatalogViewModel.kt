package com.sportzfy.live.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.core.time.Clock
import com.sportzfy.live.core.time.ZoneProvider
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.domain.repository.AppConfigRepository
import com.sportzfy.live.domain.repository.FavoritesRepository
import com.sportzfy.live.domain.repository.MatchRepository
import com.sportzfy.live.presentation.model.CatalogUiState
import com.sportzfy.live.presentation.model.DayScope
import com.sportzfy.live.presentation.model.DayWindow
import com.sportzfy.live.presentation.model.HomeView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Search, sport filter and Saved filter over the shared match feed, sliced
 * per [DayScope] for the Today, Tomorrow and All Matches tabs. One instance
 * serves every tab, so filters and search carry across them.
 */
class CatalogViewModel(
    private val matchRepository: MatchRepository,
    private val favoritesRepository: FavoritesRepository,
    private val configRepository: AppConfigRepository,
    private val clock: Clock,
    private val zones: ZoneProvider,
) : ViewModel() {

    private data class Filters(
        val category: SportCategory? = null,
        val favoritesOnly: Boolean = false,
        val query: String = "",
        val homeView: HomeView = HomeView.LIVE,
        val otherSports: Boolean = false,
        val searchActive: Boolean = false,
    )

    private data class Inputs(
        val feed: MatchFeed,
        val filters: Filters,
        val favorites: Set<String>,
        val config: AppConfig,
        val nowMillis: Long,
    )

    private val filters = MutableStateFlow(Filters())

    /** Re-evaluates the day boundaries every minute, so tabs roll over at midnight. */
    private val minuteTicks = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(60_000)
        }
    }

    private val inputs = combine(
        matchRepository.feed,
        filters,
        favoritesRepository.favoriteIds.onStart { emit(emptySet()) },
        configRepository.config,
        minuteTicks,
    ) { feed, f, favorites, config, now -> Inputs(feed, f, favorites, config, now) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val states: Map<DayScope, StateFlow<CatalogUiState>> = DayScope.entries.associateWith { scope ->
        inputs.map { it.toState(scope) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Inputs(
                feed = matchRepository.feed.value,
                filters = Filters(),
                favorites = emptySet(),
                config = configRepository.config.value,
                nowMillis = clock.nowMillis(),
            ).toState(scope),
        )
    }

    fun state(scope: DayScope): StateFlow<CatalogUiState> = states.getValue(scope)

    /**
     * Cheap to call often: the repository serves from its 60s cache. A forced
     * refresh is dropped while one is already running.
     */
    fun refresh(force: Boolean = false) {
        if (force && matchRepository.feed.value.isLoading) return
        viewModelScope.launch { matchRepository.refresh(force) }
    }

    /** Home's Live | Upcoming | All switch. Kept across tab switches. */
    fun setHomeView(value: HomeView) {
        filters.value = filters.value.copy(homeView = value)
    }

    fun selectCategory(value: SportCategory?) {
        filters.value = filters.value.copy(category = value, favoritesOnly = false, otherSports = false)
    }

    fun showFavorites() {
        filters.value = filters.value.copy(category = null, favoritesOnly = true, otherSports = false)
    }

    /** Every sport except football and cricket (drawer "Other Sports"). */
    fun showOtherSports() {
        filters.value = filters.value.copy(category = null, favoritesOnly = false, otherSports = true)
    }

    fun openSearch() {
        filters.value = filters.value.copy(searchActive = true)
    }

    /** Closes the search field and clears what was typed. */
    fun closeSearch() {
        filters.value = filters.value.copy(searchActive = false, query = "")
    }

    /** Back to the unfiltered list: no sport, Saved, Other sports or search. */
    fun resetFilters() {
        filters.value = Filters(homeView = filters.value.homeView)
    }

    fun setQuery(value: String) {
        filters.value = filters.value.copy(query = value)
    }

    fun toggleFavorite(matchId: String) {
        viewModelScope.launch { favoritesRepository.toggle(matchId) }
    }

    private fun Inputs.toState(scope: DayScope): CatalogUiState = CatalogUiState.from(
        feed = feed,
        category = filters.category,
        query = filters.query,
        favoriteIds = favorites,
        favoritesOnly = filters.favoritesOnly,
        config = config,
        scope = scope,
        window = DayWindow.at(nowMillis, zones.zone()),
        homeView = filters.homeView,
        otherSports = filters.otherSports,
        searchActive = filters.searchActive,
    )
}
