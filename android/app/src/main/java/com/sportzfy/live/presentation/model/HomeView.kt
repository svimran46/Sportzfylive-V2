package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchStatus

/**
 * The Home screen's Live | Upcoming | All switch. Home is the Today tab, so
 * every view stays within today's matches ([MatchDays.today]).
 */
enum class HomeView(val label: String) {
    /** Only matches the upstream live feed lists right now. */
    LIVE("Live"),

    /** Today's matches that haven't kicked off yet, or have no announced time. */
    UPCOMING("Upcoming"),

    /** Everything for today: live, started, upcoming and earlier today. */
    ALL("All");

    fun includes(match: Match): Boolean = when (this) {
        LIVE -> match.status == MatchStatus.LIVE
        UPCOMING -> match.status == MatchStatus.UPCOMING
        ALL -> true
    }
}
