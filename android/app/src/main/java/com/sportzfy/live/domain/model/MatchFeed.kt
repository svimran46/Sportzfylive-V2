package com.sportzfy.live.domain.model

/**
 * Snapshot of the match catalog. [matches] keeps the last good data when a
 * refresh fails, so [error] can be shown alongside stale content.
 */
data class MatchFeed(
    val matches: List<Match>,
    val updatedAtMillis: Long?,
    val isLoading: Boolean,
    val error: DataError?,
    /**
     * False when the `live` feed couldn't be loaded: no match is marked live
     * then, so "no live matches" can't be claimed either.
     */
    val liveStatusKnown: Boolean = true,
) {
    val liveCount: Int
        get() = matches.count { it.isLive }

    companion object {
        val Initial = MatchFeed(matches = emptyList(), updatedAtMillis = null, isLoading = false, error = null)
    }
}
