package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus.ENDED
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.domain.model.SportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MatchDaysTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 3_600_000L

    /** Monday 2026-10-12 12:00 UTC. */
    private val noon = 1_791_806_400_000L
    private val window = DayWindow.at(noon, utc)

    private val liveSinceYesterday = testMatch("live-old", LIVE, start = noon - 14 * hour)
    private val endedToday = testMatch("ended-today", ENDED, start = noon - 2 * hour)
    private val laterToday = testMatch("later-today", UPCOMING, start = noon + 3 * hour)
    private val tomorrow = testMatch("tomorrow", UPCOMING, SportCategory.CRICKET, start = noon + 14 * hour)
    private val thursday = testMatch("thursday", UPCOMING, start = noon + 72 * hour)
    private val yesterday = testMatch("yesterday", ENDED, start = noon - 20 * hour)
    private val noTime = testMatch("tba", UPCOMING, start = 0)
    private val all = listOf(liveSinceYesterday, endedToday, laterToday, tomorrow, thursday, yesterday, noTime)

    @Test
    fun `window follows local calendar days`() {
        assertEquals(noon - 12 * hour, window.todayStart)
        assertEquals(noon + 12 * hour, window.tomorrowStart)
        assertEquals(noon + 36 * hour, window.dayAfterStart)
        assertEquals(noon - 36 * hour, window.yesterdayStart)
    }

    @Test
    fun `month and year boundaries roll over`() {
        val newYearsEve = DayWindow.at(1_798_754_400_000L, utc) // 2026-12-31 22:00 UTC
        val jan1 = Calendar.getInstance(utc).apply { timeInMillis = newYearsEve.tomorrowStart }
        assertEquals(2027, jan1.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, jan1.get(Calendar.MONTH))
        assertEquals(1, jan1.get(Calendar.DAY_OF_MONTH))

        val endOfJanuary = DayWindow.at(1_769_860_800_000L, utc) // 2026-01-31 12:00 UTC
        val feb1 = Calendar.getInstance(utc).apply { timeInMillis = endOfJanuary.tomorrowStart }
        assertEquals(Calendar.FEBRUARY, feb1.get(Calendar.MONTH))
        assertEquals(1, feb1.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `the device timezone decides which day it is`() {
        // 20:00 UTC on the 12th is 01:30 on the 13th in India.
        val india = DayWindow.at(noon + 8 * hour, TimeZone.getTimeZone("Asia/Kolkata"))
        assertEquals(noon + 6 * hour + 30 * 60_000L, india.todayStart)
    }

    @Test
    fun `daylight saving changes give a 25 hour day`() {
        // UK clocks go back on Sunday 2026-10-25.
        val london = DayWindow.at(1_792_929_600_000L, TimeZone.getTimeZone("Europe/London"))
        assertEquals(25 * hour, london.tomorrowStart - london.todayStart)
    }

    @Test
    fun `a DST change that skips midnight doesn't shift the neighbouring days`() {
        // Chile springs forward at 00:00 on Sunday 2026-09-06: that day starts at 01:00.
        val santiago = TimeZone.getTimeZone("America/Santiago")
        fun at(day: Int, hourOfDay: Int, minute: Int = 0) = Calendar.getInstance(santiago).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, day, hourOfDay, minute)
        }.timeInMillis

        val window = DayWindow.at(at(6, 12), santiago)
        assertEquals(at(7, 0), window.tomorrowStart)
        assertEquals(at(8, 0), window.dayAfterStart)
        assertEquals(at(5, 0), window.yesterdayStart)

        val justAfterMidnight = testMatch("late", UPCOMING, start = at(7, 0, 30))
        assertEquals(listOf("late"), MatchDays.tomorrow(listOf(justAfterMidnight), window).map { it.id })
        assertEquals(emptyList<String>(), MatchDays.today(listOf(justAfterMidnight), window).map { it.id })
    }

    @Test
    fun `today is live now, kicking off today, or with no announced time`() {
        assertEquals(
            listOf("live-old", "ended-today", "later-today", "tba"),
            MatchDays.today(all, window).map { it.id },
        )
    }

    @Test
    fun `tomorrow is only the next local day and never live matches`() {
        val liveStartingTomorrow = testMatch("odd-live", LIVE, start = noon + 14 * hour)
        assertEquals(listOf("tomorrow"), MatchDays.tomorrow(all + liveStartingTomorrow, window).map { it.id })
    }

    @Test
    fun `all matches are grouped live first, then by day, then without a time`() {
        val sections = MatchDays.sectionsByDay(all, window, Locale.US)
        assertEquals(
            listOf("Live now", "Yesterday", "Today", "Tomorrow", "Thu 15 Oct", "Time to be announced"),
            sections.map { it.title },
        )
        assertEquals(listOf("ended-today", "later-today"), sections[2].matches.map { it.id })
        // Every match exactly once.
        assertEquals(all.map { it.id }.sorted(), sections.flatMap { it.matches }.map { it.id }.sorted())
    }

    private val feed = MatchFeed(all, updatedAtMillis = 1L, isLoading = false, error = null)

    private fun state(scope: DayScope) =
        CatalogUiState.from(feed, null, "", scope = scope, window = window, locale = Locale.US)

    @Test
    fun `tomorrow tab shows only tomorrow, without a spotlight`() {
        val state = state(DayScope.TOMORROW)
        assertNull(state.hero)
        assertEquals(listOf("tomorrow"), state.sections.flatMap { it.matches }.map { it.id })
        assertEquals("Tue 13 Oct", state.dateLabel)
        assertEquals(1, state.categories.first().count)
        assertEquals(1, state.categories.first { it.category == SportCategory.CRICKET }.count)
        // The header counter stays global.
        assertEquals(1, state.liveCount)
    }

    @Test
    fun `today tab never includes other days`() {
        val state = state(DayScope.TODAY)
        val ids = listOfNotNull(state.hero?.id) + state.sections.flatMap { it.matches }.map { it.id }
        assertEquals(setOf("live-old", "ended-today", "later-today", "tba"), ids.toSet())
        assertEquals("Mon 12 Oct", state.dateLabel)
    }

    @Test
    fun `all matches tab uses day sections and no spotlight`() {
        val state = state(DayScope.ALL)
        assertNull(state.hero)
        assertNull(state.dateLabel)
        assertEquals("Live now", state.sections.first().title)
        assertEquals(all.size, state.sections.sumOf { it.matches.size })
    }
}
