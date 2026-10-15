package com.sportzfy.live.presentation.navigation

import kotlinx.serialization.Serializable

/** Start destination: Home, which opens on today's live matches. */
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

/** Full-screen player for one Live TV channel. */
@Serializable
data class ChannelRoute(val channelId: String)

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute
