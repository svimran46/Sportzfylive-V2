package com.sportzfy.live.domain.model

data class Match(
    val id: String,
    val title: String,
    val category: SportCategory,
    /** Kick-off as epoch milliseconds; 0 when upstream omits it. */
    val startTimeMillis: Long,
    val posterUrl: String?,
    val homeTeam: Team?,
    val awayTeam: Team?,
    val sources: List<StreamSource>,
    val status: MatchStatus,
    val isPopular: Boolean,
) {
    val isLive: Boolean
        get() = status == MatchStatus.LIVE

    val hasTeams: Boolean
        get() = homeTeam != null && awayTeam != null
}
