package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Announcement
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.DataError
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.domain.model.SportCategory
import java.util.Locale

data class CategoryOption(val category: SportCategory?, val count: Int) {
    val label: String
        get() = category?.displayName ?: "All"
}

enum class SectionKind(val title: String, val status: MatchStatus) {
    LIVE("Live now", MatchStatus.LIVE),
    STARTED("Started", MatchStatus.STARTED),
    UPCOMING("Upcoming", MatchStatus.UPCOMING),
    ENDED("Earlier today", MatchStatus.ENDED),
}

/** A titled group of match cards. Status sections carry their [kind]; day sections don't. */
data class MatchSection(
    val kind: SectionKind?,
    val matches: List<Match>,
    val key: String = kind?.name ?: "section",
    val title: String = kind?.title.orEmpty(),
)

data class CatalogUiState(
    /** Nothing has loaded yet, successfully or not. */
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DataError? = null,
    val hasData: Boolean = false,
    /** Live matches across all categories; null when the live feed is unavailable. */
    val liveCount: Int? = 0,
    val categories: List<CategoryOption> = listOf(CategoryOption(null, 0)),
    val selectedCategory: SportCategory? = null,
    /** "Saved" pill: only matches the user bookmarked. */
    val favoritesOnly: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    /** Saved matches present in today's feed. */
    val savedCount: Int = 0,
    val query: String = "",
    val hero: Match? = null,
    val sections: List<MatchSection> = emptyList(),
    /** Owner's message from the remote config. */
    val announcement: Announcement? = null,
    /** Day slice this state was built for; null means the whole feed. */
    val scope: DayScope? = null,
    /** "Tue 13 Oct" for the scope's day (Today/Tomorrow), when known. */
    val dateLabel: String? = null,
    /** The Home switch position; null on screens other than Home. */
    val homeView: HomeView? = null,
    /** False when the live feed couldn't be loaded, so nothing is marked live. */
    val liveStatusKnown: Boolean = true,
    /** Matches in the list, spotlight included, after every filter. */
    val listCount: Int = 0,
    /** "Other sports" filter from the drawer: everything but football and cricket. */
    val otherSports: Boolean = false,
    /** The search field is open (shared by every fixture tab). */
    val searchActive: Boolean = false,
) {
    val isEmpty: Boolean
        get() = hero == null && sections.isEmpty()

    companion object {
        /** The sports with their own drawer entry; "Other sports" is the rest. */
        val MAIN_SPORTS = setOf(SportCategory.FOOTBALL, SportCategory.CRICKET)

        fun from(
            feed: MatchFeed,
            category: SportCategory?,
            query: String,
            favoriteIds: Set<String> = emptySet(),
            favoritesOnly: Boolean = false,
            config: AppConfig = AppConfig.Default,
            scope: DayScope? = null,
            window: DayWindow? = null,
            locale: Locale = Locale.getDefault(),
            homeView: HomeView? = null,
            otherSports: Boolean = false,
            searchActive: Boolean = false,
        ): CatalogUiState {
            val visible = feed.matches.filter { it.category !in config.hiddenCategories }
            // The Live | Upcoming | All switch only exists on Home (Today).
            val view = homeView?.takeIf { scope == DayScope.TODAY }
            // Pills, counts and the list all describe the scope's day (and Home's
            // view); the header live counter stays global.
            val inScope = if (scope != null && window != null) MatchDays.inScope(visible, scope, window) else visible
            val all = if (view == null) inScope else inScope.filter(view::includes)
            val counts = all.groupingBy { it.category }.eachCount()
            val categories = buildList {
                add(CategoryOption(null, all.size))
                SportCategory.featured
                    .filter { it !in config.hiddenCategories }
                    .forEach { add(CategoryOption(it, counts[it] ?: 0)) }
                counts.entries
                    .filter { it.key !in SportCategory.featured }
                    .sortedByDescending { it.value }
                    .forEach { add(CategoryOption(it.key, it.value)) }
            }

            val filtered = CatalogFilter.apply(all, category, query, onlyIds = favoriteIds.takeIf { favoritesOnly })
                .let { list -> if (otherSports && !favoritesOnly) list.filter { it.category !in MAIN_SPORTS } else list }
            // The banner is for browsing; while searching or viewing saved
            // matches every hit stays in the list.
            val showsHero = scope == null || scope == DayScope.TODAY
            val hero = if (showsHero && query.isBlank() && !favoritesOnly) {
                // A match pinned in the remote config wins while it is in the feed.
                config.featuredMatchId?.let { id -> filtered.firstOrNull { it.id == id } }
                    ?: CatalogFilter.spotlight(filtered)
            } else {
                null
            }
            val rest = if (hero == null) filtered else filtered.filter { it.id != hero.id }
            val sections = if (scope == DayScope.ALL && window != null) {
                MatchDays.sectionsByDay(rest, window, locale)
            } else if (view == HomeView.LIVE || view == HomeView.UPCOMING) {
                // One status only: the Home heading already names it.
                listOfNotNull(rest.takeIf { it.isNotEmpty() }?.let { MatchSection(null, it, key = "home-${view.name}") })
            } else {
                SectionKind.entries.mapNotNull { kind ->
                    rest.filter { it.status == kind.status }
                        .takeIf { it.isNotEmpty() }
                        ?.let { MatchSection(kind, it) }
                }
            }
            val dateLabel = window?.let {
                when (scope) {
                    DayScope.TODAY -> it.dateLabel(it.todayStart, locale)
                    DayScope.TOMORROW -> it.dateLabel(it.tomorrowStart, locale)
                    else -> null
                }
            }

            return CatalogUiState(
                isInitialLoading = feed.updatedAtMillis == null && feed.error == null,
                isRefreshing = feed.isLoading,
                error = feed.error,
                hasData = feed.updatedAtMillis != null,
                liveCount = visible.count { it.isLive }.takeIf { feed.liveStatusKnown },
                categories = categories,
                selectedCategory = category,
                favoritesOnly = favoritesOnly,
                favoriteIds = favoriteIds,
                savedCount = all.count { it.id in favoriteIds },
                query = query,
                hero = hero,
                sections = sections,
                announcement = config.announcement,
                scope = scope,
                dateLabel = dateLabel,
                homeView = view,
                liveStatusKnown = feed.liveStatusKnown,
                listCount = filtered.size,
                otherSports = otherSports && !favoritesOnly,
                searchActive = searchActive || query.isNotEmpty(),
            )
        }
    }
}
