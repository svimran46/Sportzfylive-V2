package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.domain.model.ChannelCategory.CRICKET
import com.sportzfy.live.domain.model.ChannelCategory.FOOTBALL
import com.sportzfy.live.domain.model.ChannelCategory.OTHERS
import com.sportzfy.live.domain.model.ChannelFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun testChannel(name: String, category: ChannelCategory) = Channel(
    id = name.lowercase().replace(' ', '-'),
    name = name,
    logoUrl = null,
    category = category,
    streamUrl = "https://tv.example/${name.lowercase().replace(' ', '-')}",
)

class ChannelsUiStateTest {

    private val channels = listOf(
        testChannel("Sky Sports Football", FOOTBALL),
        testChannel("Sky Sports Cricket", CRICKET),
        testChannel("Willow Cricket", CRICKET),
        testChannel("ESPN", OTHERS),
    )
    private val feed = ChannelFeed(channels, updatedAtMillis = 1L, isLoading = false, error = null)

    private fun names(state: ChannelsUiState) = state.channels.map { it.name }

    @Test
    fun `chips list All plus each group that has channels`() {
        val state = ChannelsUiState.from(feed, null, "")
        assertEquals(listOf("All" to 4, "Football" to 1, "Cricket" to 2, "Others" to 1), state.categories.map { it.label to it.count })
        assertTrue(state.showCategories)
    }

    @Test
    fun `groups without channels get no chip`() {
        val state = ChannelsUiState.from(feed.copy(channels = channels.filter { it.category != FOOTBALL }), null, "")
        assertEquals(listOf("All", "Cricket", "Others"), state.categories.map { it.label })
    }

    @Test
    fun `chips are hidden when every channel is in one group`() {
        val state = ChannelsUiState.from(feed.copy(channels = channels.filter { it.category == CRICKET }), null, "")
        assertFalse(state.showCategories)
    }

    @Test
    fun `a chip filters the list`() {
        assertEquals(listOf("Sky Sports Cricket", "Willow Cricket"), names(ChannelsUiState.from(feed, CRICKET, "")))
    }

    @Test
    fun `search matches every word in the name or group`() {
        assertEquals(listOf("Sky Sports Cricket"), names(ChannelsUiState.from(feed, null, "sky cricket")))
        assertEquals(listOf("Sky Sports Football"), names(ChannelsUiState.from(feed, null, "football")))
        assertEquals(emptyList<String>(), names(ChannelsUiState.from(feed, FOOTBALL, "willow")))
    }

    @Test
    fun `a filter whose group disappeared falls back to All`() {
        val state = ChannelsUiState.from(feed.copy(channels = channels.filter { it.category != FOOTBALL }), FOOTBALL, "")
        assertNull(state.selectedCategory)
        assertEquals(3, state.channels.size)
    }

    @Test
    fun `loading and error flags follow the feed`() {
        assertTrue(ChannelsUiState.from(ChannelFeed.Initial, null, "").isInitialLoading)
        val failed = ChannelsUiState.from(ChannelFeed.Initial.copy(error = com.sportzfy.live.domain.model.DataError.NETWORK), null, "")
        assertFalse(failed.isInitialLoading)
        assertFalse(failed.hasData)
    }
}
