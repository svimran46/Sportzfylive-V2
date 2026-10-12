package com.sportzfy.live.data.mapper

import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.data.model.RemoteMatchDto
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.testing.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchMapperTest {

    private val now = 1_700_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun decode(raw: String): List<RemoteMatchDto> = MatchMapper.decodeList(NetworkJson, json(raw))

    private fun match(id: String, date: Long, sources: String = """[{"source":"alpha","id":"$id-a"}]""") =
        """{"id":"$id","title":"$id title","category":"football","date":$date,"sources":$sources}"""

    @Test
    fun `maps every field of a well-formed match`() {
        val dto = decode(
            """[{"id":"m1","title":"Real Madrid vs Manchester City","category":"football","date":${now + hour},
              "poster":"/api/images/poster/ucl.webp","popular":true,
              "teams":{"home":{"name":"Real Madrid","badge":"rm"},"away":{"name":"Manchester City","badge":"https://cdn/x.webp"}},
              "sources":[{"source":"alpha","id":"match-101"},{"source":"bravo","id":"match-102"},{"source":"","id":"x"}]}]"""
        ).single()

        val match = MatchMapper.toDomain(dto, now, isLive = false)!!

        assertEquals("m1", match.id)
        assertEquals(SportCategory.FOOTBALL, match.category)
        assertEquals(MatchStatus.UPCOMING, match.status)
        assertEquals("https://streamed.pk/api/images/poster/ucl.webp", match.posterUrl)
        assertEquals("https://streamed.pk/api/images/badge/rm.webp", match.homeTeam!!.badgeUrl)
        assertEquals("https://cdn/x.webp", match.awayTeam!!.badgeUrl)
        assertEquals(listOf(StreamSource("alpha", "match-101"), StreamSource("bravo", "match-102")), match.sources)
        assertTrue(match.isPopular)
    }

    @Test
    fun `drops undecodable entries without failing the list`() {
        val list = decode("""[${match("ok", now)}, {"id":{"nested":true}}, 42, ${match("ok2", now)}]""")
        assertEquals(listOf("ok", "ok2"), list.map { it.id })
    }

    @Test
    fun `accepts quoted numeric dates`() {
        assertEquals(now, decode("""[{"id":"q","title":"t","date":"$now"}]""").single().date)
    }

    @Test
    fun `non-array payload yields an empty list`() {
        assertTrue(decode("""{"error":"nope"}""").isEmpty())
    }

    @Test
    fun `derives a title from teams and skips matches without id or title`() {
        val list = decode(
            """[{"id":"t","teams":{"home":{"name":"A"},"away":{"name":"B"}}},{"id":"none"},{"title":"no id"}]"""
        )
        val matches = list.mapNotNull { MatchMapper.toDomain(it, now, isLive = false) }
        assertEquals(listOf("A vs B"), matches.map { it.title })
    }

    @Test
    fun `tags live matches and appends live-only ones`() {
        val today = decode("""[${match("a", now - hour)}, ${match("b", now + hour)}, ${match("c", now - 5 * hour)}]""")
        val live = decode("""[${match("a", now - hour, """[{"source":"charlie","id":"a-c"}]""")}, ${match("z", now - hour)}]""")

        val merged = MatchMapper.merge(today, live, now).associateBy { it.id }

        assertEquals(MatchStatus.LIVE, merged.getValue("a").status)
        assertEquals(MatchStatus.UPCOMING, merged.getValue("b").status)
        assertEquals(MatchStatus.ENDED, merged.getValue("c").status)
        assertEquals(MatchStatus.LIVE, merged.getValue("z").status)
        assertEquals(
            listOf(StreamSource("alpha", "a-a"), StreamSource("charlie", "a-c")),
            merged.getValue("a").sources,
        )
    }

    @Test
    fun `falls back to kick-off time when the live feed is unknown`() {
        val today = decode("""[${match("recent", now - hour)}, ${match("old", now - 5 * hour)}, ${match("soon", now + hour)}]""")

        val merged = MatchMapper.merge(today, live = null, nowMillis = now).associateBy { it.id }

        assertEquals(MatchStatus.LIVE, merged.getValue("recent").status)
        assertEquals(MatchStatus.ENDED, merged.getValue("old").status)
        assertEquals(MatchStatus.UPCOMING, merged.getValue("soon").status)
    }

    @Test
    fun `orders live, then upcoming by kick-off, then ended newest first`() {
        val today = decode(
            """[${match("e1", now - 10 * hour)}, ${match("u2", now + 2 * hour)}, ${match("l", now - hour)},
               ${match("u1", now + hour)}, ${match("e2", now - 5 * hour)}]"""
        )
        val live = decode("""[${match("l", now - hour)}]""")

        assertEquals(listOf("l", "u1", "u2", "e2", "e1"), MatchMapper.merge(today, live, now).map { it.id })
    }

    @Test
    fun `de-duplicates repeated ids`() {
        val today = decode("""[${match("a", now + hour)}, ${match("a", now + 2 * hour)}]""")
        val merged = MatchMapper.merge(today, emptyList(), now)
        assertEquals(1, merged.size)
        assertEquals(now + hour, merged.single().startTimeMillis)
    }

    @Test
    fun `missing kick-off counts as upcoming`() {
        val dto = decode("""[{"id":"x","title":"t"}]""").single()
        val match = MatchMapper.toDomain(dto, now, isLive = null)!!
        assertEquals(MatchStatus.UPCOMING, match.status)
        assertNull(match.posterUrl)
    }
}
