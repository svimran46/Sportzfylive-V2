package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.DataError
import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus.ENDED
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.domain.model.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogUiStateTest {

    private val matches = listOf(
        testMatch("live-1", LIVE, home = "Real Madrid", away = "Manchester City"),
        testMatch("live-2", LIVE, SportCategory.CRICKET, home = "India", away = "Australia", popular = true),
        testMatch("up-1", UPCOMING, SportCategory.TENNIS, title = "Wimbledon Final"),
        testMatch("up-2", UPCOMING, SportCategory.FIGHT, title = "UFC 300"),
        testMatch("end-1", ENDED, SportCategory.FOOTBALL, home = "Arsenal", away = "Chelsea"),
    )
    private val feed = MatchFeed(matches, updatedAtMillis = 1L, isLoading = false, error = null)

    @Test
    fun `initial feed is loading`() {
        val state = CatalogUiState.from(MatchFeed.Initial, null, "")
        assertTrue(state.isInitialLoading)
        assertFalse(state.hasData)
    }

    @Test
    fun `failed first load is not loading`() {
        val state = CatalogUiState.from(MatchFeed.Initial.copy(error = DataError.NETWORK), null, "")
        assertFalse(state.isInitialLoading)
        assertEquals(DataError.NETWORK, state.error)
    }

    @Test
    fun `hero is the popular live match and is not repeated in sections`() {
        val state = CatalogUiState.from(feed, null, "")
        assertEquals("live-2", state.hero?.id)
        assertEquals(listOf(SectionKind.LIVE, SectionKind.UPCOMING, SectionKind.ENDED), state.sections.map { it.kind })
        assertEquals(listOf("live-1"), state.sections.first().matches.map { it.id })
        assertEquals(2, state.liveCount)
    }

    @Test
    fun `category pills list featured sports first, then other sports present`() {
        val state = CatalogUiState.from(feed, null, "")
        assertEquals(
            listOf(null) + SportCategory.featured + SportCategory.FIGHT,
            state.categories.map { it.category },
        )
        assertEquals(5, state.categories.first().count)
        assertEquals(2, state.categories.first { it.category == SportCategory.FOOTBALL }.count)
        assertEquals(0, state.categories.first { it.category == SportCategory.BASKETBALL }.count)
    }

    @Test
    fun `selecting a category filters hero and sections`() {
        val state = CatalogUiState.from(feed, SportCategory.FOOTBALL, "")
        assertEquals("live-1", state.hero?.id)
        assertEquals(listOf("end-1"), state.sections.flatMap { it.matches }.map { it.id })
        assertEquals(2, state.liveCount)
    }

    @Test
    fun `search drops the hero and matches every token`() {
        val state = CatalogUiState.from(feed, null, "  madrid  CITY ")
        assertNull(state.hero)
        assertEquals(listOf("live-1"), state.sections.flatMap { it.matches }.map { it.id })
    }

    @Test
    fun `search matches sport names`() {
        val state = CatalogUiState.from(feed, null, "cricket")
        assertEquals(listOf("live-2"), state.sections.flatMap { it.matches }.map { it.id })
    }

    @Test
    fun `no hits is empty`() {
        assertTrue(CatalogUiState.from(feed, SportCategory.BASKETBALL, "").isEmpty)
        assertTrue(CatalogUiState.from(feed, null, "zzz").isEmpty)
    }

    @Test
    fun `spotlight falls back to upcoming`() {
        val upcomingOnly = listOf(testMatch("a", UPCOMING), testMatch("b", UPCOMING, popular = true))
        assertEquals("b", CatalogFilter.spotlight(upcomingOnly)?.id)
        assertNull(CatalogFilter.spotlight(listOf(testMatch("e", ENDED))))
    }
}
