package com.sportzfy.live.presentation.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.domain.repository.FavoritesRepository
import com.sportzfy.live.domain.repository.MatchRepository
import com.sportzfy.live.domain.repository.StreamRepository
import com.sportzfy.live.presentation.model.WatchLogic
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WatchUiState(
    val match: Match? = null,
    val isMatchMissing: Boolean = false,
    val streams: List<StreamItem> = emptyList(),
    val isResolvingStreams: Boolean = true,
    val failedSourceCount: Int = 0,
    val selectedStreamKey: String? = null,
    val related: List<Match> = emptyList(),
    val isFavorite: Boolean = false,
) {
    val selectedStream: StreamItem?
        get() = streams.firstOrNull { it.key == selectedStreamKey }
}

/** [initialMatchId] comes from the [com.sportzfy.live.presentation.navigation.WatchRoute] argument. */
class WatchViewModel(
    initialMatchId: String,
    private val matchRepository: MatchRepository,
    private val streamRepository: StreamRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    private data class StreamLoad(
        val isLoading: Boolean = true,
        val streams: List<StreamItem> = emptyList(),
        val failedSources: Int = 0,
        val matchMissing: Boolean = false,
    )

    private val matchId = MutableStateFlow(initialMatchId)
    private val streamLoad = MutableStateFlow(StreamLoad())
    private val chosenKey = MutableStateFlow<String?>(null)
    private var loadJob: Job? = null

    val uiState: StateFlow<WatchUiState> =
        combine(
            matchRepository.feed,
            matchId,
            streamLoad,
            chosenKey,
            favoritesRepository.favoriteIds.onStart { emit(emptySet()) },
        ) { feed, id, load, chosen, favorites ->
            val match = feed.matches.firstOrNull { it.id == id }
            // Keep the user's pick while it exists; otherwise fall back to the best stream.
            val selected = chosen?.takeIf { key -> load.streams.any { it.key == key } }
                ?: WatchLogic.preferredStream(load.streams)?.key
            WatchUiState(
                match = match,
                isMatchMissing = load.matchMissing && match == null,
                streams = load.streams,
                isResolvingStreams = load.isLoading,
                failedSourceCount = load.failedSources,
                selectedStreamKey = selected,
                related = WatchLogic.related(feed.matches, match),
                isFavorite = id in favorites,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchUiState())

    init {
        load(matchId.value)
    }

    fun selectStream(key: String) {
        chosenKey.value = key
    }

    /** Switches to another match in place, keeping the player mounted. */
    fun selectMatch(id: String) {
        if (id == matchId.value) return
        matchId.value = id
        chosenKey.value = null
        load(id)
    }

    fun retryStreams() = load(matchId.value)

    fun toggleFavorite() {
        val id = matchId.value
        viewModelScope.launch { favoritesRepository.toggle(id) }
    }

    private fun load(id: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            streamLoad.value = StreamLoad(isLoading = true)
            val match = matchRepository.getMatch(id)
            if (match == null) {
                streamLoad.value = StreamLoad(isLoading = false, matchMissing = true)
                return@launch
            }
            val resolution = streamRepository.resolveStreams(match.sources)
            streamLoad.value = StreamLoad(
                isLoading = false,
                streams = resolution.streams,
                failedSources = resolution.failedSources.size,
            )
        }
    }
}
