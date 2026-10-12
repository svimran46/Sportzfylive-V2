package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.domain.model.SportCategory

object CatalogFilter {

    private val WHITESPACE = Regex("\\s+")

    /** Everyday names people type for a sport, beyond its display name. */
    private val ALIASES: Map<SportCategory, List<String>> = mapOf(
        SportCategory.FOOTBALL to listOf("soccer"),
        SportCategory.AMERICAN_FOOTBALL to listOf("nfl"),
        SportCategory.BASKETBALL to listOf("nba"),
        SportCategory.HOCKEY to listOf("nhl", "ice hockey"),
        SportCategory.BASEBALL to listOf("mlb"),
        SportCategory.MOTORSPORT to listOf("f1", "formula 1", "motogp", "nascar", "racing"),
        SportCategory.FIGHT to listOf("ufc", "mma", "boxing", "wwe"),
        SportCategory.CRICKET to listOf("ipl", "t20", "test match"),
        SportCategory.BILLIARDS to listOf("snooker", "pool"),
    )

    /**
     * Keeps matches in [category] (null = all) whose title, team names, sport
     * or sport aliases contain every whitespace-separated token of [query].
     * League names are matched through the title, which upstream uses to
     * carry them (e.g. "Premier League: Arsenal vs Chelsea").
     */
    fun apply(
        matches: List<Match>,
        category: SportCategory?,
        query: String,
        onlyIds: Set<String>? = null,
    ): List<Match> {
        val tokens = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        return matches.filter { match ->
            (category == null || match.category == category) &&
                (onlyIds == null || match.id in onlyIds) &&
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
        get() = (listOfNotNull(title, homeTeam?.name, awayTeam?.name, category.displayName, category.apiKey) +
            ALIASES[category].orEmpty())
            .joinToString(" ")
            .lowercase()
}
