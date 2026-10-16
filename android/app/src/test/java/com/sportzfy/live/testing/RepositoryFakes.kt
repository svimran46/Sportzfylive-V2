package com.sportzfy.live.testing

import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelFeed
import com.sportzfy.live.domain.model.ChannelLibrary
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.domain.model.StreamResolution
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.domain.repository.AppConfigRepository
import com.sportzfy.live.domain.repository.ChannelLibraryRepository
import com.sportzfy.live.domain.repository.ChannelRepository
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

    override suspend fun clear() {
        ids.value = emptySet()
    }
}

class FakeAppConfigRepository(initial: AppConfig = AppConfig.Default) : AppConfigRepository {
    private val state = MutableStateFlow(initial)
    override val config: StateFlow<AppConfig> = state

    fun emit(config: AppConfig) {
        state.value = config
    }

    override suspend fun refresh(force: Boolean) = Unit
}

/** Channel directory with a fixed list; [refreshCalls] counts loads. */
class FakeChannelRepository(channels: List<Channel> = emptyList()) : ChannelRepository {
    private val state = MutableStateFlow(ChannelFeed(channels, updatedAtMillis = 1L, isLoading = false, error = null))
    override val feed: StateFlow<ChannelFeed> = state

    var refreshCalls = 0
        private set
    var forcedRefreshCalls = 0
        private set

    fun emit(feed: ChannelFeed) {
        state.value = feed
    }

    override suspend fun refresh(force: Boolean) {
        refreshCalls++
        if (force) forcedRefreshCalls++
    }

    override suspend fun getChannel(id: String): Channel? = state.value.channels.firstOrNull { it.id == id }
}

/** In-memory starred and recent channels. */
class FakeChannelLibraryRepository(initial: ChannelLibrary = ChannelLibrary.Empty) : ChannelLibraryRepository {
    private val state = MutableStateFlow(initial)
    override val library: Flow<ChannelLibrary> = state

    val value: ChannelLibrary
        get() = state.value

    override suspend fun toggleFavorite(channelId: String) {
        val ids = state.value.favoriteIds
        state.value = state.value.copy(favoriteIds = if (channelId in ids) ids - channelId else listOf(channelId) + ids)
    }

    override suspend fun recordWatched(channelId: String) {
        state.value = state.value.copy(recentIds = listOf(channelId) + (state.value.recentIds - channelId))
    }
}
