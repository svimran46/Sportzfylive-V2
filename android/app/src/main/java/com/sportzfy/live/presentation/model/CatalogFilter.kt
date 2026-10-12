package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.domain.model.SportCategory

object CatalogFilter {

    private val WHITESPACE = Regex("\\s+")

    /**
     * Keeps matches in [category] (null = all) whose title, team names or
     * sport contain every whitespace-separated token of [query].
     */
    fun apply(matches: List<Match>, category: SportCategory?, query: String): List<Match> {
        val tokens = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        return matches.filter { match ->
            (category == null || match.category == category) &&
                tokens.all { token -> match.searchText.contains(token) }
        }
    }

    /** The featured match: popular live, any live, popular upcoming, then any upcoming. */
    fun spotlight(matches: List<Match>): Match? =
        matches.firstOrNull { it.isLive && it.isPopular }
            ?: matches.firstOrNull { it.isLive }
            ?: matches.firstOrNull { it.status == MatchStatus.UPCOMING && it.isPopular }
            ?: matches.firstOrNull { it.status == MatchStatus.UPCOMING }

    private val Match.searchText: String
        get() = listOfNotNull(title, homeTeam?.name, awayTeam?.name, category.displayName, category.apiKey)
            .joinToString(" ")
            .lowercase()
}
