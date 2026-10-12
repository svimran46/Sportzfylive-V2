package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.domain.model.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherSportsFilterTest {

    private val feed = MatchFeed(
        listOf(
            testMatch("football", UPCOMING, SportCategory.FOOTBALL),
            testMatch("cricket", UPCOMING, SportCategory.CRICKET),
            testMatch("tennis", UPCOMING, SportCategory.TENNIS),
            testMatch("f1", UPCOMING, SportCategory.MOTORSPORT),
        ),
        updatedAtMillis = 1L,
        isLoading = false,
        error = null,
    )

    private fun ids(state: CatalogUiState) =
        (listOfNotNull(state.hero?.id) + state.sections.flatMap { it.matches }.map { it.id }).toSet()

    @Test
    fun `other sports leaves out football and cricket`() {
        val state = CatalogUiState.from(feed, null, "", otherSports = true)
        assertEquals(setOf("tennis", "f1"), ids(state))
        assertTrue(state.otherSports)
    }

    @Test
    fun `saved matches take precedence over other sports`() {
        val state = CatalogUiState.from(feed, null, "", favoriteIds = setOf("football"), favoritesOnly = true, otherSports = true)
        assertEquals(setOf("football"), ids(state))
        assertFalse(state.otherSports)
    }

    @Test
    fun `typing a query counts as an open search`() {
        assertTrue(CatalogUiState.from(feed, null, "ten").searchActive)
        assertFalse(CatalogUiState.from(feed, null, "").searchActive)
        assertTrue(CatalogUiState.from(feed, null, "", searchActive = true).searchActive)
    }
}
