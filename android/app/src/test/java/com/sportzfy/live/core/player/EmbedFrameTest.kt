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
        // The pop-up networks are always refused; the wider ad list only with ad blocking on.
        assertEquals(
            PlayerConfig(
                PlayerMode.DIRECT,
                blockedHosts = AdHosts.ALWAYS_BLOCKED,
                blockedResourceHosts = AdHosts.ALWAYS_BLOCKED,
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
    fun `pop-up networks' requests are refused, the wider ad list only navigates`() {
        val config = PlayerConfig.from(AppConfig(adBlocking = true, extraBlockedHosts = setOf("bad.com")))
        assertEquals(AdHosts.ALWAYS_BLOCKED + "bad.com", config.blockedResourceHosts)
        val policy = PlayerUrlPolicy(blockedHosts = config.blockedHosts, blockedResourceHosts = config.blockedResourceHosts)
        for (url in listOf(
            "https://c1.popads.net/pop.js",
            "https://www.popcash.net/x.js",
            "https://pl123.adsterra.com/a.js",
            "https://go.propellerads.com/x",
            "https://googleads.g.doubleclick.net/pagead/id",
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://onclkds.com/apu.php",
            "https://acscdn.com/script/aclib.js",
            "https://s10.histats.com/js15_as.js",
            "https://x.bad.com/a.js",
        )) {
            assertEquals(url, BLOCK, policy.resource(url))
        }
        assertEquals(ALLOW, policy.resource("https://a.exosrv.com/ad-provider.js"))
        assertEquals(BLOCK, policy.navigation("https://a.exosrv.com/frame", isMainFrame = false))
    }
}
