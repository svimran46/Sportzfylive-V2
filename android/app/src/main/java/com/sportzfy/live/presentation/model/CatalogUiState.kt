package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Announcement
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.DataError
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.domain.model.SportCategory

data class CategoryOption(val category: SportCategory?, val count: Int) {
    val label: String
        get() = category?.displayName ?: "All"
}

enum class SectionKind(val title: String, val status: MatchStatus) {
    LIVE("Live now", MatchStatus.LIVE),
    UPCOMING("Upcoming", MatchStatus.UPCOMING),
    ENDED("Earlier today", MatchStatus.ENDED),
}

data class MatchSection(val kind: SectionKind, val matches: List<Match>)

data class CatalogUiState(
    /** Nothing has loaded yet, successfully or not. */
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DataError? = null,
    val hasData: Boolean = false,
    /** Live matches across all categories, for the header counter. */
    val liveCount: Int = 0,
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
) {
    val isEmpty: Boolean
        get() = hero == null && sections.isEmpty()

    companion object {
        fun from(
            feed: MatchFeed,
            category: SportCategory?,
            query: String,
            favoriteIds: Set<String> = emptySet(),
            favoritesOnly: Boolean = false,
            config: AppConfig = AppConfig.Default,
        ): CatalogUiState {
            val all = feed.matches.filter { it.category !in config.hiddenCategories }
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
            // The banner is for browsing; while searching or viewing saved
            // matches every hit stays in the list.
            val hero = if (query.isBlank() && !favoritesOnly) {
                // A match pinned in the remote config wins while it is in the feed.
                config.featuredMatchId?.let { id -> filtered.firstOrNull { it.id == id } }
                    ?: CatalogFilter.spotlight(filtered)
            } else {
                null
            }
            val rest = if (hero == null) filtered else filtered.filter { it.id != hero.id }
            val sections = SectionKind.entries.mapNotNull { kind ->
                rest.filter { it.status == kind.status }
                    .takeIf { it.isNotEmpty() }
                    ?.let { MatchSection(kind, it) }
            }

            return CatalogUiState(
                isInitialLoading = feed.updatedAtMillis == null && feed.error == null,
                isRefreshing = feed.isLoading,
                error = feed.error,
                hasData = feed.updatedAtMillis != null,
                liveCount = all.count { it.isLive },
                categories = categories,
                selectedCategory = category,
                favoritesOnly = favoritesOnly,
                favoriteIds = favoriteIds,
                savedCount = all.count { it.id in favoriteIds },
                query = query,
                hero = hero,
                sections = sections,
                announcement = config.announcement,
            )
        }
    }
}
