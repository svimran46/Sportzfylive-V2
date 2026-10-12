package com.sportzfy.live.testing

import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.domain.model.StreamResolution
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.domain.repository.FavoritesRepository
import com.sportzfy.live.domain.repository.MatchRepository
import com.sportzfy.live.domain.repository.StreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeMatchRepository(matches: List<Match> = emptyList()) : MatchRepository {
    private val state = MutableStateFlow(MatchFeed(matches, updatedAtMillis = 1L, isLoading = false, error = null))
    override val feed: StateFlow<MatchFeed> = state

    var refreshCalls = 0
        private set
    var forcedRefreshCalls = 0
        private set

    fun emit(feed: MatchFeed) {
        state.value = feed
    }

    override suspend fun refresh(force: Boolean) {
        refreshCalls++
        if (force) forcedRefreshCalls++
    }

    override suspend fun getMatch(id: String): Match? = state.value.matches.firstOrNull { it.id == id }
}

/** Streams per source group; groups in [failing] report as failed. */
class FakeStreamRepository(
    private val streams: Map<StreamSource, List<StreamItem>> = emptyMap(),
    private val failing: Set<StreamSource> = emptySet(),
) : StreamRepository {
    val requests = mutableListOf<List<StreamSource>>()

    override suspend fun resolveStreams(sources: List<StreamSource>): StreamResolution {
        requests += sources
        return StreamResolution(
            streams = sources.filter { it !in failing }.flatMap { streams[it].orEmpty() },
            failedSources = sources.filter { it in failing },
        )
    }
}

class FakeFavoritesRepository(initial: Set<String> = emptySet()) : FavoritesRepository {
    private val ids = MutableStateFlow(initial)
    override val favoriteIds: Flow<Set<String>> = ids

    override suspend fun toggle(matchId: String) {
        ids.value = if (matchId in ids.value) ids.value - matchId else ids.value + matchId
    }
}
