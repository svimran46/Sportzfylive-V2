package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.domain.model.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndFavoritesTest {

    private val matches = listOf(
        testMatch("epl", LIVE, title = "Premier League: Arsenal vs Chelsea", popular = true),
        testMatch("f1", UPCOMING, SportCategory.MOTORSPORT, title = "Japanese Grand Prix"),
        testMatch("ufc", UPCOMING, SportCategory.FIGHT, title = "Fight Night: Smith vs Jones"),
        testMatch("nba", LIVE, SportCategory.BASKETBALL, home = "Lakers", away = "Celtics"),
    )
    private val feed = MatchFeed(matches, updatedAtMillis = 1L, isLoading = false, error = null)

    private fun ids(state: CatalogUiState) = listOfNotNull(state.hero?.id) + state.sections.flatMap { it.matches }.map { it.id }

    @Test
    fun `league names in the title are searchable`() {
        assertEquals(listOf("epl"), ids(CatalogUiState.from(feed, null, "premier league")))
    }

    @Test
    fun `sport aliases match`() {
        assertEquals(listOf("epl"), ids(CatalogUiState.from(feed, null, "soccer")))
        assertEquals(listOf("f1"), ids(CatalogUiState.from(feed, null, "F1")))
        assertEquals(listOf("ufc"), ids(CatalogUiState.from(feed, null, "mma")))
        assertEquals(listOf("nba"), ids(CatalogUiState.from(feed, null, "nba celtics")))
    }

    @Test
    fun `saved filter shows only bookmarked matches without a hero`() {
        val state = CatalogUiState.from(feed, null, "", favoriteIds = setOf("f1", "nba", "gone"), favoritesOnly = true)
        assertNull(state.hero)
        assertEquals(setOf("f1", "nba"), ids(state).toSet())
        assertTrue(state.favoritesOnly)
        assertEquals(2, state.savedCount)
    }

    @Test
    fun `saved count is reported while browsing`() {
        val state = CatalogUiState.from(feed, null, "", favoriteIds = setOf("epl"))
        assertFalse(state.favoritesOnly)
        assertEquals(1, state.savedCount)
        assertEquals("epl", state.hero?.id)
        assertEquals(setOf("epl"), state.favoriteIds)
    }

    @Test
    fun `search narrows the saved view`() {
        val state = CatalogUiState.from(feed, null, "lakers", favoriteIds = setOf("f1", "nba"), favoritesOnly = true)
        assertEquals(listOf("nba"), ids(state))
    }

    @Test
    fun `nothing saved gives an empty saved view`() {
        assertTrue(CatalogUiState.from(feed, null, "", favoritesOnly = true).isEmpty)
    }
}
