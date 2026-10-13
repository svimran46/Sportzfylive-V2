package com.sportzfy.live.data.channels

import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.domain.model.ChannelCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelParserTest {

    private fun entry(name: String, url: String, logo: String = "https://img.example/$name.png", category: String? = null) =
        """{"name":"$name","logo":"$logo","genre":"Sports","url":"$url"${category?.let { ""","category":"$it"""" } ?: ""}}"""

    private fun parse(raw: String) = ChannelParser.parse(NetworkJson, raw)

    @Test
    fun `reads the JavaScript-wrapped directory`() {
        val raw = "const channels = [\n  ${entry("ESPN", "https://tv.example/espn")},\n  ${entry("TNT", "https://tv.example/tnt")}\n];\n"
        val channels = parse(raw)!!
        assertEquals(listOf("ESPN", "TNT"), channels.map { it.name })
        assertEquals("https://tv.example/espn", channels.first().streamUrl)
        assertEquals("tv.example", channels.first().sourceHost)
    }

    @Test
    fun `reads a plain JSON array too`() {
        assertEquals(1, parse("[${entry("ESPN", "https://tv.example/espn")}]")!!.size)
    }

    @Test
    fun `sandbox flag is off unless the channel sets it to true`() {
        val raw = """[
          {"name":"Plain","url":"https://tv.example/plain"},
          {"name":"Boxed","url":"https://tv.example/boxed","sandbox":true},
          {"name":"Quoted","url":"https://tv.example/quoted","sandbox":"true"},
          {"name":"Odd","url":"https://tv.example/odd","sandbox":1},
          {"name":"Off","url":"https://tv.example/off","sandbox":false}
        ]"""
        val channels = parse(raw)!!
        assertEquals(listOf("Plain", "Boxed", "Quoted", "Odd", "Off"), channels.map { it.name })
        assertEquals(listOf(false, true, true, false, false), channels.map { it.sandboxed })
    }

    @Test
    fun `unreadable input is rejected rather than read as empty`() {
        assertNull(parse("<html>rate limited</html>"))
        assertNull(parse("const channels = [ {broken ];"))
    }

    @Test
    fun `decodes repeatedly escaped ampersands in logo URLs`() {
        val logo = "https://img.example/a.jpg?w=3840&amp;amp;amp;amp;q=75"
        val channel = parse("[${entry("beIN Sports", "https://tv.example/bein", logo)}]")!!.single()
        assertEquals("https://img.example/a.jpg?w=3840&q=75", channel.logoUrl)
    }

    @Test
    fun `skips entries without a name or an https stream, and repeated streams`() {
        val raw = """[
            ${entry("Good", "https://tv.example/good")},
            {"url":"https://tv.example/nameless"},
            ${entry("Plain http", "http://tv.example/http")},
            ${entry("Copy", "https://tv.example/good")}
        ]"""
        assertEquals(listOf("Good"), parse(raw)!!.map { it.name })
    }

    @Test
    fun `non-https logos are dropped, not loaded`() {
        val channel = parse("[${entry("ESPN", "https://tv.example/espn", "http://img.example/espn.png")}]")!!.single()
        assertNull(channel.logoUrl)
    }

    @Test
    fun `ids are unique slugs of the name`() {
        val raw = "[${entry("Sky Sports+", "https://tv.example/1")}, ${entry("Sky Sports +", "https://tv.example/2")}]"
        assertEquals(listOf("sky-sports", "sky-sports-2"), parse(raw)!!.map { it.id })
    }

    @Test
    fun `groups channels by name when no category is given`() {
        fun category(name: String) = ChannelParser.categoryOf(null, name)
        assertEquals(ChannelCategory.CRICKET, category("Sky Sports Cricket"))
        assertEquals(ChannelCategory.CRICKET, category("Willow Cricket 2"))
        assertEquals(ChannelCategory.CRICKET, category("Fox Sports 501 (Cricket)"))
        assertEquals(ChannelCategory.FOOTBALL, category("Sky Sports Football"))
        assertEquals(ChannelCategory.FOOTBALL, category("Sky Sports Premier League"))
        assertEquals(ChannelCategory.FOOTBALL, category("DAZN LaLiga"))
        assertEquals(ChannelCategory.FOOTBALL, category("Sky Sport Bundesliga"))
        assertEquals(ChannelCategory.FOOTBALL, category("SPORTDIGITAL FUSSBALL"))
        // "Footy" is Australian rules, and "PL" here is Poland, not the Premier League.
        assertEquals(ChannelCategory.OTHERS, category("Fox Sports 504 (Footy)"))
        assertEquals(ChannelCategory.OTHERS, category("CANAL+ Sport PL"))
        assertEquals(ChannelCategory.OTHERS, category("Cartoon Network"))
    }

    @Test
    fun `an explicit category wins over the name`() {
        val raw = "[${entry("ESPN", "https://tv.example/espn", category = "Football")}, ${entry("Willow", "https://tv.example/w", category = "other")}]"
        assertEquals(listOf(ChannelCategory.FOOTBALL, ChannelCategory.OTHERS), parse(raw)!!.map { it.category })
    }
}
