package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Announcement
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.domain.model.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RemoteConfigCatalogTest {

    private val feed = MatchFeed(
        listOf(
            testMatch("live-pop", LIVE, popular = true),
            testMatch("later", UPCOMING, start = 10),
            testMatch("fight", LIVE, SportCategory.FIGHT),
        ),
        updatedAtMillis = 1L,
        isLoading = false,
        error = null,
    )

    @Test
    fun `featured match overrides the spotlight while it is in the feed`() {
        assertEquals("later", CatalogUiState.from(feed, null, "", config = AppConfig(featuredMatchId = "later")).hero?.id)
        assertEquals("live-pop", CatalogUiState.from(feed, null, "", config = AppConfig(featuredMatchId = "gone")).hero?.id)
    }

    @Test
    fun `hidden sports disappear from matches, pills and the live count`() {
        val state = CatalogUiState.from(feed, null, "", config = AppConfig(hiddenCategories = setOf(SportCategory.FIGHT, SportCategory.TENNIS)))
        val ids = listOfNotNull(state.hero?.id) + state.sections.flatMap { it.matches }.map { it.id }
        assertEquals(setOf("live-pop", "later"), ids.toSet())
        assertFalse(state.categories.any { it.category == SportCategory.FIGHT || it.category == SportCategory.TENNIS })
        assertEquals(1, state.liveCount)
    }

    @Test
    fun `announcement is passed through`() {
        val banner = Announcement("Hello", "https://example.com")
        assertEquals(banner, CatalogUiState.from(feed, null, "", config = AppConfig(announcement = banner)).announcement)
    }
}
