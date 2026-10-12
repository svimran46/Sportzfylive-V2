package com.sportzfy.live.presentation.navigation

import kotlinx.serialization.Serializable

/** Start destination: today's fixtures (Phase 3 turns it into Live Matches). */
@Serializable
data object TodayRoute

@Serializable
data object TomorrowRoute

@Serializable
data object LiveTvRoute

@Serializable
data object AllMatchesRoute

@Serializable
data class WatchRoute(val matchId: String)
