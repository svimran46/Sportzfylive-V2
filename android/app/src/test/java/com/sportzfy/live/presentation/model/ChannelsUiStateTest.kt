package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.domain.model.ChannelCategory.CRICKET
import com.sportzfy.live.domain.model.ChannelCategory.FOOTBALL
import com.sportzfy.live.domain.model.ChannelCategory.OTHERS
import com.sportzfy.live.domain.model.ChannelFeed
import com.sportzfy.live.domain.model.ChannelLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun testChannel(name: String, category: ChannelCategory, country: String? = null) = Channel(
    id = name.lowercase().replace(' ', '-'),
    name = name,
    logoUrl = null,
    category = category,
    streamUrl = "https://tv.example/${name.lowercase().replace(' ', '-')}",
    country = country,
)

class ChannelsUiStateTest {

    private val channels = listOf(
        testChannel("Sky Sports Football", FOOTBALL, "United Kingdom"),
        testChannel("Sky Sports Cricket", CRICKET, "United Kingdom"),
        testChannel("Willow Cricket", CRICKET, "USA"),
        testChannel("ESPN", OTHERS, "USA"),
    )
    private val feed = ChannelFeed(channels, updatedAtMillis = 1L, isLoading = false, error = null)

    private fun state(filters: ChannelFilters = ChannelFilters(), library: ChannelLibrary = ChannelLibrary.Empty) =
        ChannelsUiState.from(feed, filters, library)

    private fun names(state: ChannelsUiState) = state.channels.map { it.name }

    @Test
    fun `chips list All plus each group that has channels`() {
        val state = state()
        assertEquals(listOf("All" to 4, "Football" to 1, "Cricket" to 2, "Others" to 1), state.categories.map { it.label to it.count })
        assertTrue(state.showCategories)
    }

    @Test
    fun `groups without channels get no chip`() {
        val state = ChannelsUiState.from(feed.copy(channels = channels.filter { it.category != FOOTBALL }))
        assertEquals(listOf("All", "Cricket", "Others"), state.categories.map { it.label })
    }

    @Test
    fun `chips are hidden when every channel is in one group`() {
        val state = ChannelsUiState.from(feed.copy(channels = channels.filter { it.category == CRICKET }))
        assertFalse(state.showCategories)
    }

    @Test
    fun `a chip filters the list`() {
        assertEquals(listOf("Sky Sports Cricket", "Willow Cricket"), names(state(ChannelFilters(category = CRICKET))))
    }

    @Test
    fun `search matches every word in the name, group or country`() {
        assertEquals(listOf("Sky Sports Cricket"), names(state(ChannelFilters(query = "sky cricket"))))
        assertEquals(listOf("Sky Sports Football"), names(state(ChannelFilters(query = "football"))))
        assertEquals(listOf("Willow Cricket", "ESPN"), names(state(ChannelFilters(query = "usa"))))
        assertEquals(emptyList<String>(), names(state(ChannelFilters(category = FOOTBALL, query = "willow"))))
    }

    @Test
    fun `a filter whose group disappeared falls back to All`() {
        val state = ChannelsUiState.from(feed.copy(channels = channels.filter { it.category != FOOTBALL }), ChannelFilters(category = FOOTBALL))
        assertNull(state.selectedCategory)
        assertEquals(3, state.channels.size)
    }

    @Test
    fun `countries are World first, then A to Z with counts`() {
        val state = state()
        assertEquals(listOf("World" to 4, "United Kingdom" to 2, "USA" to 2), state.countries.map { it.label to it.count })
        assertEquals("World", state.selectedCountry.label)
        assertTrue(state.showCountries)
    }

    @Test
    fun `a country narrows the grid and its sport chips`() {
        val state = state(ChannelFilters(country = "USA"))
        assertEquals(listOf("Willow Cricket", "ESPN"), names(state))
        assertEquals(listOf("All" to 2, "Cricket" to 1, "Others" to 1), state.categories.map { it.label to it.count })
        assertEquals(CountryOption("USA", 2), state.selectedCountry)
    }

    @Test
    fun `a country that vanished falls back to World`() {
        val state = state(ChannelFilters(country = "Narnia"))
        assertNull(state.selectedCountry.name)
        assertEquals(4, state.channels.size)
    }

    @Test
    fun `a playlist without countries shows no picker`() {
        val state = ChannelsUiState.from(feed.copy(channels = channels.map { it.copy(country = null) }))
        assertFalse(state.showCountries)
    }

    @Test
    fun `A to Z sorts by name, Browse keeps the playlist order`() {
        assertEquals(listOf("Sky Sports Football", "Sky Sports Cricket", "Willow Cricket", "ESPN"), names(state()))
        assertEquals(
            listOf("ESPN", "Sky Sports Cricket", "Sky Sports Football", "Willow Cricket"),
            names(state(ChannelFilters(sort = ChannelSort.A_Z))),
        )
    }

    @Test
    fun `shelves appear once they hold a channel`() {
        assertEquals(emptyList<ChannelShelf>(), state().shelves)
        val library = ChannelLibrary(favoriteIds = listOf("espn"), recentIds = listOf("gone-channel"))
        assertEquals(listOf(ChannelShelf.FAVORITES), state(library = library).shelves)
        // The selected shelf keeps its chip even when it is empty.
        assertEquals(
            listOf(ChannelShelf.RECENT, ChannelShelf.FAVORITES),
            state(ChannelFilters(shelf = ChannelShelf.RECENT), library).shelves,
        )
    }

    @Test
    fun `a shelf lists its channels newest first across every country and sport`() {
        val library = ChannelLibrary(recentIds = listOf("willow-cricket", "sky-sports-football", "gone-channel"))
        val state = state(ChannelFilters(shelf = ChannelShelf.RECENT, country = "USA", category = CRICKET), library)
        assertEquals(listOf("Willow Cricket", "Sky Sports Football"), names(state))
    }

    @Test
    fun `favorite ids are exposed for the stars`() {
        val state = state(library = ChannelLibrary(favoriteIds = listOf("espn")))
        assertEquals(setOf("espn"), state.favoriteIds)
    }

    @Test
    fun `loading and error flags follow the feed`() {
        assertTrue(ChannelsUiState.from(ChannelFeed.Initial).isInitialLoading)
        val failed = ChannelsUiState.from(ChannelFeed.Initial.copy(error = com.sportzfy.live.domain.model.DataError.NETWORK))
        assertFalse(failed.isInitialLoading)
        assertFalse(failed.hasData)
    }
}
