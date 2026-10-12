package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus.ENDED
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.STARTED
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** Home's Live | Upcoming | All switch, applied within today's matches. */
class HomeViewTest {

    private val hour = 3_600_000L

    /** Monday 2026-10-12 12:00 UTC. */
    private val noon = 1_791_806_400_000L
    private val window = DayWindow.at(noon, TimeZone.getTimeZone("UTC"))

    private val matches = listOf(
        testMatch("live", LIVE, start = noon - hour),
        testMatch("live-since-yesterday", LIVE, start = noon - 14 * hour),
        testMatch("later", UPCOMING, start = noon + 2 * hour),
        testMatch("tba", UPCOMING),
        testMatch("started", STARTED, start = noon - hour),
        testMatch("ended", ENDED, start = noon - 5 * hour),
        testMatch("tomorrow", UPCOMING, start = noon + 24 * hour),
    )
    private val feed = MatchFeed(matches, updatedAtMillis = 1L, isLoading = false, error = null)

    private fun home(view: HomeView, feed: MatchFeed = this.feed, scope: DayScope = DayScope.TODAY) =
        CatalogUiState.from(feed, null, "", scope = scope, window = window, locale = Locale.US, homeView = view)

    private fun CatalogUiState.ids() = (listOfNotNull(hero?.id) + sections.flatMap { it.matches }.map { it.id }).toSet()

    @Test
    fun `live shows only matches the live feed lists`() {
        val state = home(HomeView.LIVE)
        assertEquals(setOf("live", "live-since-yesterday"), state.ids())
        assertEquals(2, state.listCount)
        // The heading names the status, so the list has no section titles.
        assertEquals(listOf(""), state.sections.map { it.title })
    }

    @Test
    fun `a started match is never treated as live`() {
        val state = home(HomeView.LIVE, feed.copy(matches = matches.filter { it.status != LIVE }))
        assertEquals(emptySet<String>(), state.ids())
        assertEquals(0, state.listCount)
    }

    @Test
    fun `upcoming shows today's matches that haven't kicked off`() {
        assertEquals(setOf("later", "tba"), home(HomeView.UPCOMING).ids())
    }

    @Test
    fun `all shows everything for today in status sections`() {
        val state = home(HomeView.ALL)
        assertEquals(setOf("live", "live-since-yesterday", "later", "tba", "started", "ended"), state.ids())
        assertEquals(listOf("Live now", "Started", "Upcoming", "Earlier today"), state.sections.map { it.title })
    }

    @Test
    fun `sport pill counts describe the current view`() {
        assertEquals(2, home(HomeView.LIVE).categories.first().count)
        assertEquals(6, home(HomeView.ALL).categories.first().count)
    }

    @Test
    fun `the switch only applies to Home`() {
        val state = home(HomeView.LIVE, scope = DayScope.TOMORROW)
        assertEquals(setOf("tomorrow"), state.ids())
        assertNull(state.homeView)
    }

    @Test
    fun `no live count is claimed when the live feed is unavailable`() {
        val state = home(HomeView.LIVE, feed.copy(matches = matches.filter { it.status != LIVE }, liveStatusKnown = false))
        assertNull(state.liveCount)
        assertFalse(state.liveStatusKnown)
    }
}
