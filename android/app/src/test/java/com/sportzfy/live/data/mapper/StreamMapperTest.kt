package com.sportzfy.live.data.mapper

import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.testing.json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMapperTest {

    private val group = StreamSource("alpha", "match-101")

    private fun map(raw: String?): List<StreamItem> =
        StreamMapper.toStreamItems(NetworkJson, raw?.let(::json), group)

    @Test
    fun `maps the documented payload`() {
        val items = map("""[{"streamNo":1,"language":"English","hd":true,"source":"alpha"},{"streamNo":2,"language":"Hindi","hd":false}]""")
        assertEquals(
            listOf(
                StreamItem("alpha", "match-101", 1, "English", true),
                StreamItem("alpha", "match-101", 2, "Hindi", false),
            ),
            items,
        )
        assertEquals("https://embed.st/embed/alpha/match-101/1", items.first().embedUrl)
    }

    @Test
    fun `never uses upstream embed URLs`() {
        val item = map("""[{"streamNo":3,"embedUrl":"https://evil.example/x.m3u8"}]""").single()
        assertEquals("https://embed.st/embed/alpha/match-101/3", item.embedUrl)
    }

    @Test
    fun `accepts wrapped payloads`() {
        assertEquals(1, map("""{"streams":[{"streamNo":1}]}""").size)
        assertEquals(2, map("""{"data":[{"streamNo":1},{"streamNo":2}]}""").size)
        assertTrue(map("""{"other":[]}""").isEmpty())
        assertTrue(map(null).isEmpty())
    }

    @Test
    fun `reads alternate field names and loose types`() {
        val items = map(
            """[{"stream_no":"4","lang":" Spanish ","hd":"true"},
               {"streamNo":5,"quality":"1080p FHD"},
               {"streamNo":6,"label":"HD"},
               {"streamNo":7,"isHd":true},
               {"streamNo":8,"label":"shd-ish"}]"""
        )
        assertEquals(listOf(4, 5, 6, 7, 8), items.map { it.streamNo })
        assertEquals("Spanish", items[0].language)
        assertEquals(listOf(true, true, true, true, false), items.map { it.isHd })
    }

    @Test
    fun `falls back to position for missing or invalid numbers and primitive entries`() {
        val items = map("""[{"language":"English"},"raw",{"streamNo":0},{"streamNo":{"x":1}}]""")
        assertEquals(listOf(1, 2, 3, 4), items.map { it.streamNo })
    }

    @Test
    fun `de-duplicates by number, language and HD`() {
        val items = map(
            """[{"streamNo":2,"language":"Spanish","hd":true},
               {"streamNo":2,"language":"Spanish","hd":true},
               {"streamNo":2,"language":"Spanish","hd":false}]"""
        )
        assertEquals(2, items.size)
    }
}
