package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.MatchStatus.ENDED
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.domain.model.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchLogicTest {

    private fun stream(no: Int, language: String = "", hd: Boolean = false) = StreamItem("alpha", "m", no, language, hd)

    @Test
    fun `prefers English HD, then any HD, then the first stream`() {
        assertEquals(3, WatchLogic.preferredStream(listOf(stream(1), stream(2, "Hindi", true), stream(3, "english", true)))?.streamNo)
        assertEquals(2, WatchLogic.preferredStream(listOf(stream(1, "English"), stream(2, "Hindi", true)))?.streamNo)
        assertEquals(1, WatchLogic.preferredStream(listOf(stream(1), stream(2)))?.streamNo)
        assertNull(WatchLogic.preferredStream(emptyList()))
    }

    @Test
    fun `related puts the same sport first, then live, then kick-off, and skips ended`() {
        val current = testMatch("cur", LIVE, SportCategory.FOOTBALL)
        val all = listOf(
            current,
            testMatch("cricket-live", LIVE, SportCategory.CRICKET, start = 1),
            testMatch("football-later", UPCOMING, SportCategory.FOOTBALL, start = 300),
            testMatch("football-soon", UPCOMING, SportCategory.FOOTBALL, start = 200),
            testMatch("football-live", LIVE, SportCategory.FOOTBALL, start = 100),
            testMatch("football-ended", ENDED, SportCategory.FOOTBALL),
        )
        assertEquals(
            listOf("football-live", "football-soon", "football-later", "cricket-live"),
            WatchLogic.related(all, current).map { it.id },
        )
    }

    @Test
    fun `related is capped`() {
        val all = (1..20).map { testMatch("m$it", LIVE) }
        assertEquals(WatchLogic.RELATED_LIMIT, WatchLogic.related(all, null).size)
    }

    @Test
    fun `stream labels match the design`() {
        assertEquals("Stream 1 HD • English", WatchLogic.streamLabel(0, stream(7, "English", true)))
        assertEquals("Stream 2 • Hindi", WatchLogic.streamLabel(1, stream(1, "Hindi")))
        assertEquals("Stream 3", WatchLogic.streamLabel(2, stream(1)))
    }
}
