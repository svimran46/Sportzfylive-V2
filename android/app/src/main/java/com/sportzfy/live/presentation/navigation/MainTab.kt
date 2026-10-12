package com.sportzfy.live.presentation.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.presentation.components.NavBarItem

/** The four bottom-bar destinations, in bar order. */
enum class MainTab(val item: NavBarItem) {
    TODAY(NavBarItem("today", "Today", SportzfyIcons.Today)),
    TOMORROW(NavBarItem("tomorrow", "Tomorrow", SportzfyIcons.Tomorrow)),
    LIVE_TV(NavBarItem("live_tv", "Live TV", SportzfyIcons.LiveTv)),
    ALL_MATCHES(NavBarItem("all_matches", "All Matches", SportzfyIcons.AllMatches));

    companion object {
        val items: List<NavBarItem> = entries.map { it.item }

        fun fromKey(key: String): MainTab = entries.first { it.item.key == key }

        /** The tab a destination belongs to, or null (e.g. the player). */
        fun of(destination: NavDestination?): MainTab? = when {
            destination == null -> null
            destination.hasRoute<TodayRoute>() -> TODAY
            destination.hasRoute<TomorrowRoute>() -> TOMORROW
            destination.hasRoute<LiveTvRoute>() -> LIVE_TV
            destination.hasRoute<AllMatchesRoute>() -> ALL_MATCHES
            else -> null
        }
    }
}
