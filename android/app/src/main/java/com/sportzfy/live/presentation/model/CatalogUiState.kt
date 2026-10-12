package com.sportzfy.live.presentation.model

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
    val query: String = "",
    val hero: Match? = null,
    val sections: List<MatchSection> = emptyList(),
) {
    val isEmpty: Boolean
        get() = hero == null && sections.isEmpty()

    companion object {
        fun from(feed: MatchFeed, category: SportCategory?, query: String): CatalogUiState {
            val all = feed.matches
            val counts = all.groupingBy { it.category }.eachCount()
            val categories = buildList {
                add(CategoryOption(null, all.size))
                SportCategory.featured.forEach { add(CategoryOption(it, counts[it] ?: 0)) }
                counts.entries
                    .filter { it.key !in SportCategory.featured }
                    .sortedByDescending { it.value }
                    .forEach { add(CategoryOption(it.key, it.value)) }
            }

            val filtered = CatalogFilter.apply(all, category, query)
            // The banner is for browsing; while searching every hit stays in the list.
            val hero = if (query.isBlank()) CatalogFilter.spotlight(filtered) else null
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
                liveCount = feed.liveCount,
                categories = categories,
                selectedCategory = category,
                query = query,
                hero = hero,
                sections = sections,
            )
        }
    }
}
