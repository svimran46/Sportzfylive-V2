package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.domain.model.ChannelFeed
import com.sportzfy.live.domain.model.ChannelLibrary
import com.sportzfy.live.domain.model.DataError

data class ChannelCategoryOption(val category: ChannelCategory?, val count: Int) {
    val label: String
        get() = category?.label ?: "All"
}

/** One country in the picker; [name] null is "World", every country. */
data class CountryOption(val name: String?, val count: Int) {
    val label: String
        get() = name ?: WORLD

    companion object {
        const val WORLD = "World"
    }
}

/** Browse keeps the playlist's own order (or recency on a shelf); A–Z sorts by name. */
enum class ChannelSort(val label: String) {
    BROWSE("Browse"),
    A_Z("A–Z"),
}

/** The user's own lists, shown as chips ahead of the sports. */
enum class ChannelShelf(val label: String) {
    RECENT("Recent"),
    FAVORITES("Favorites"),
}

/** What the user picked above the grid. */
data class ChannelFilters(
    val category: ChannelCategory? = null,
    val query: String = "",
    /** null is World. */
    val country: String? = null,
    val sort: ChannelSort = ChannelSort.BROWSE,
    /** When set, the grid shows this shelf instead of the directory. */
    val shelf: ChannelShelf? = null,
)

data class ChannelsUiState(
    /** Nothing has loaded yet, successfully or not. */
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DataError? = null,
    val hasData: Boolean = false,
    /** "All" plus each category that has at least one channel in the selected country. */
    val categories: List<ChannelCategoryOption> = emptyList(),
    val selectedCategory: ChannelCategory? = null,
    /** World first, then every country in the playlist, A–Z. */
    val countries: List<CountryOption> = emptyList(),
    val selectedCountry: CountryOption = CountryOption(null, 0),
    val sort: ChannelSort = ChannelSort.BROWSE,
    /** Shelves with something on them (or the one selected), in chip order. */
    val shelves: List<ChannelShelf> = emptyList(),
    val selectedShelf: ChannelShelf? = null,
    val favoriteIds: Set<String> = emptySet(),
    val query: String = "",
    val channels: List<Channel> = emptyList(),
) {
    /** Chips are pointless when every channel falls in one group. */
    val showCategories: Boolean
        get() = categories.size > 2

    /** A playlist with no countries (a plain `channels.json`) gets no picker. */
    val showCountries: Boolean
        get() = countries.size > 1

    companion object {
        fun from(
            feed: ChannelFeed,
            filters: ChannelFilters = ChannelFilters(),
            library: ChannelLibrary = ChannelLibrary.Empty,
        ): ChannelsUiState {
            val countryCounts = feed.channels.mapNotNull { it.country }.groupingBy { it }.eachCount()
            val countries = listOf(CountryOption(null, feed.channels.size)) +
                countryCounts.keys.sortedBy { it.lowercase() }.map { CountryOption(it, countryCounts.getValue(it)) }
            // A country or group that vanished from the directory falls back to World / All.
            val selectedCountry = countries.firstOrNull { it.name == filters.country } ?: countries.first()
            val inCountry = feed.channels.filter { selectedCountry.name == null || it.country == selectedCountry.name }

            val counts = inCountry.groupingBy { it.category }.eachCount()
            val categories = listOf(ChannelCategoryOption(null, inCountry.size)) +
                ChannelCategory.entries.mapNotNull { c -> counts[c]?.let { ChannelCategoryOption(c, it) } }
            val selectedCategory = filters.category?.takeIf { it in counts }

            val byId = feed.channels.associateBy { it.id }
            fun shelfIds(shelf: ChannelShelf) = when (shelf) {
                ChannelShelf.RECENT -> library.recentIds
                ChannelShelf.FAVORITES -> library.favoriteIds
            }
            val shelves = ChannelShelf.entries.filter { shelf ->
                shelf == filters.shelf || shelfIds(shelf).any { it in byId }
            }

            // Shelves are the user's own lists: every country and sport, in their own order.
            val base = when (val shelf = filters.shelf) {
                null -> inCountry.filter { selectedCategory == null || it.category == selectedCategory }
                else -> shelfIds(shelf).mapNotNull { byId[it] }
            }
            val words = filters.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val matching = base.filter { channel ->
                val text = "${channel.name} ${channel.category.label} ${channel.country.orEmpty()}".lowercase()
                words.all { it in text }
            }
            val channels = when (filters.sort) {
                ChannelSort.BROWSE -> matching
                ChannelSort.A_Z -> matching.sortedBy { it.name.lowercase() }
            }

            return ChannelsUiState(
                isInitialLoading = feed.updatedAtMillis == null && feed.error == null,
                isRefreshing = feed.isLoading,
                error = feed.error,
                hasData = feed.updatedAtMillis != null,
                categories = categories,
                selectedCategory = selectedCategory,
                countries = countries,
                selectedCountry = selectedCountry,
                sort = filters.sort,
                shelves = shelves,
                selectedShelf = filters.shelf,
                favoriteIds = library.favoriteIds.toSet(),
                query = filters.query,
                channels = channels,
            )
        }
    }
}
