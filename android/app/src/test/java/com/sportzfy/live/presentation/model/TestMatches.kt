package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.domain.model.Team

internal fun testMatch(
    id: String,
    status: MatchStatus = MatchStatus.UPCOMING,
    category: SportCategory = SportCategory.FOOTBALL,
    start: Long = 0L,
    home: String? = null,
    away: String? = null,
    title: String = if (home != null && away != null) "$home vs $away" else id,
    popular: Boolean = false,
) = Match(
    id = id,
    title = title,
    category = category,
    startTimeMillis = start,
    posterUrl = null,
    homeTeam = home?.let { Team(it, null) },
    awayTeam = away?.let { Team(it, null) },
    sources = listOf(StreamSource("alpha", id)),
    status = status,
    isPopular = popular,
)
