package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MatchTimeFormatterTest {

    private val zone = TimeZone.getTimeZone("UTC")
    private val formatter = MatchTimeFormatter(Locale.US, zone, use24Hour = true)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance(zone, Locale.US).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute)
    }.timeInMillis

    private val now = at(12, 18)

    @Test
    fun `labels kick-off relative to today`() {
        assertEquals("Today 20:30", formatter.kickoff(at(12, 20, 30), now))
        assertEquals("Tomorrow 01:00", formatter.kickoff(at(13, 1), now))
        assertEquals("Yesterday 23:00", formatter.kickoff(at(11, 23), now))
        assertEquals("Thu 15 Oct 19:45", formatter.kickoff(at(15, 19, 45), now))
    }

    @Test
    fun `counts down the last hour`() {
        assertEquals("In 25 min", formatter.kickoff(at(12, 18, 25), now))
        assertEquals("Starting now", formatter.kickoff(now + 30_000, now))
    }

    @Test
    fun `unknown kick-off`() {
        assertEquals("Time TBA", formatter.kickoff(0, now))
    }

    @Test
    fun `12-hour clock`() {
        val twelve = MatchTimeFormatter(Locale.US, zone, use24Hour = false)
        assertEquals("Today 8:30 PM", twelve.kickoff(at(12, 20, 30), now))
    }

    @Test
    fun `status text`() {
        assertEquals("LIVE", formatter.status(testMatch("a", MatchStatus.LIVE), now))
        assertEquals("Ended", formatter.status(testMatch("a", MatchStatus.ENDED), now))
        assertEquals("Today 20:30", formatter.status(testMatch("a", MatchStatus.UPCOMING, start = at(12, 20, 30)), now))
    }

    @Test
    fun `sources label`() {
        assertEquals("1 source", sourcesLabel(1))
        assertEquals("3 sources", sourcesLabel(3))
    }
}
