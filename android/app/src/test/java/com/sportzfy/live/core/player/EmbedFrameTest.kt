package com.sportzfy.live.core.player

import com.sportzfy.live.core.player.PlayerUrlPolicy.Decision.ALLOW
import com.sportzfy.live.core.player.PlayerUrlPolicy.Decision.BLOCK
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
        // Embeds load directly, like a browser tab, referred by streamed.pk like its own embed.
        // The pop-up networks are always kept off the top-level page; the wider ad list only
        // with ad blocking on. No request is blocked unless the owner adds the host.
        assertEquals(
            PlayerConfig(
                PlayerMode.DIRECT,
                blockedHosts = AdHosts.ALWAYS_BLOCKED,
                blockedResourceHosts = emptySet(),
                referer = "https://streamed.pk/",
            ),
            PlayerConfig.from(AppConfig.Default),
        )
        assertTrue(PlayerConfig.from(AppConfig(adBlocking = true)).blockedHosts.containsAll(AdHosts.DEFAULT))
        val noAds = PlayerConfig.from(AppConfig(adBlocking = false, extraBlockedHosts = setOf("bad.com"), playerMode = PlayerMode.IFRAME))
        assertEquals(PlayerMode.IFRAME, noAds.mode)
        assertEquals(AdHosts.ALWAYS_BLOCKED + "bad.com", noAds.blockedHosts)
        assertTrue(PlayerConfig.from(AppConfig(adBlocking = true, extraBlockedHosts = setOf("bad.com"))).blockedHosts.containsAll(AdHosts.DEFAULT + "bad.com"))
    }

    @Test
    fun `ad scripts load so inline player setup does not crash, ad redirects stay blocked`() {
        val config = PlayerConfig.from(AppConfig(adBlocking = true, extraBlockedHosts = setOf("bad.com")))
        assertEquals(setOf("bad.com"), config.blockedResourceHosts)
        val policy = PlayerUrlPolicy(
            streamUrl = "https://embed.st/embed/golf/x/1",
            blockedHosts = config.blockedHosts,
            blockedResourceHosts = config.blockedResourceHosts,
        )
        for (host in AdHosts.ALWAYS_BLOCKED) {
            assertEquals(host, ALLOW, policy.resource("https://$host/script.js"))
            assertEquals(host, BLOCK, policy.navigation("https://$host/landing", isMainFrame = true))
        }
        assertEquals(BLOCK, policy.resource("https://x.bad.com/a.js"))
        assertEquals(ALLOW, policy.navigation("https://player.other-cdn.example/embed", isMainFrame = false))
    }
}
