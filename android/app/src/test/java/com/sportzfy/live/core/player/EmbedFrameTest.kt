package com.sportzfy.live.core.player

import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.model.PlayerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedFrameTest {

    @Test
    fun `wraps the embed in an unsandboxed iframe`() {
        val html = EmbedFrame.html("https://embed.st/embed/alpha/match-101/1")
        assertTrue(html.contains("""<iframe src="https://embed.st/embed/alpha/match-101/1" """))
        assertTrue(html.contains("allowfullscreen"))
        assertTrue(html.contains("autoplay"))
        assertFalse(html.contains("sandbox"))
    }

    @Test
    fun `escapes the url attribute`() {
        val html = EmbedFrame.html("""https://embed.st/a"><script>x</script>&b""")
        assertTrue(html.contains("""src="https://embed.st/a&quot;&gt;&lt;script&gt;x&lt;/script&gt;&amp;b""""))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun `player config follows the remote config`() {
        // Ads play by default: the stream sites need them to serve the stream.
        assertEquals(PlayerConfig(PlayerMode.IFRAME, emptySet()), PlayerConfig.from(AppConfig.Default))
        assertTrue(PlayerConfig.from(AppConfig(adBlocking = true)).blockedHosts.containsAll(AdHosts.DEFAULT))
        val noAds = PlayerConfig.from(AppConfig(adBlocking = false, extraBlockedHosts = setOf("bad.com"), playerMode = PlayerMode.DIRECT))
        assertEquals(PlayerMode.DIRECT, noAds.mode)
        assertEquals(setOf("bad.com"), noAds.blockedHosts)
        assertTrue(PlayerConfig.from(AppConfig(adBlocking = true, extraBlockedHosts = setOf("bad.com"))).blockedHosts.containsAll(AdHosts.DEFAULT + "bad.com"))
    }
}
