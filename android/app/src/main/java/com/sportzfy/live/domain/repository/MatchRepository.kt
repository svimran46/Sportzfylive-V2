package com.sportzfy.live.domain.repository

import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchFeed
import kotlinx.coroutines.flow.StateFlow

interface MatchRepository {
    /** Today's fixtures with live matches tagged; updated on every refresh. */
    val feed: StateFlow<MatchFeed>

    /**
     * Refreshes from upstream. A no-op while the last successful load is
     * younger than the cache TTL, unless [force] is true.
     */
    suspend fun refresh(force: Boolean = false)

    /** Looks [id] up in the cached feed, refreshing once if it is missing. */
    suspend fun getMatch(id: String): Match?
}
