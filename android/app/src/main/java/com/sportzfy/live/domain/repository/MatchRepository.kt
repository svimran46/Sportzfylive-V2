package com.sportzfy.live.domain.repository

import com.sportzfy.live.domain.model.Match
import kotlinx.coroutines.flow.Flow

interface MatchRepository {
    /** Today's fixtures with live matches tagged; emits on every refresh. */
    val matches: Flow<Result<List<Match>>>

    /**
     * Refreshes from upstream. Served from cache when the last successful load
     * is younger than the TTL, unless [force] is true.
     */
    suspend fun refresh(force: Boolean = false)

    suspend fun getMatch(id: String): Match?
}
