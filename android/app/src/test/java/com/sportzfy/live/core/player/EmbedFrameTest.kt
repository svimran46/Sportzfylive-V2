package com.sportzfy.live.core.player

import com.sportzfy.live.BuildConfig
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
    fun `sandboxed frame drops pop-up and top-navigation rights but keeps dialogs`() {
        val html = EmbedFrame.html("https://channel.example/live", EmbedFrame.SANDBOX)
        assertTrue(html.contains("""sandbox="${EmbedFrame.SANDBOX}" """))
        assertFalse(EmbedFrame.SANDBOX.contains("allow-popups"))
        assertFalse(EmbedFrame.SANDBOX.contains("allow-top-navigation"))
        assertTrue(EmbedFrame.SANDBOX.contains("allow-modals"))
    }

    @Test
    fun `sandboxed wrapper runs under an origin no channel can use`() {
        // A channel on the wrapper's origin would be same-origin with it and could drop the sandbox.
        assertFalse(EmbedFrame.SANDBOX_BASE_URL.contains("streamed.pk"))
        assertFalse(EmbedFrame.SANDBOX_BASE_URL.contains("embed.st"))
        assertTrue(EmbedFrame.SANDBOX_BASE_URL.endsWith(".invalid/"))
    }

    @Test
    fun `framed page is matched ignoring a trailing slash only`() {
        assertTrue(EmbedFrame.isFramedPage("https://channel.example/", "https://channel.example"))
        assertTrue(EmbedFrame.isFramedPage("https://channel.example/live/", "https://channel.example/live"))
        assertFalse(EmbedFrame.isFramedPage("https://channel.example/ad.js", "https://channel.example/live"))
    }

    @Test
    fun `player config follows the remote config`() {
        // Ads play by default: the stream sites need them to serve the stream.
        // Embeds load directly, like a browser tab, referred by streamed.pk like its own embed.
        assertEquals(
            PlayerConfig(PlayerMode.DIRECT, blockedHosts = emptySet(), blockedResourceHosts = emptySet(), referer = "https://streamed.pk/"),
            PlayerConfig.from(AppConfig.Default),
        )
        assertTrue(PlayerConfig.from(AppConfig(adBlocking = true)).blockedHosts.containsAll(AdHosts.DEFAULT))
        val noAds = PlayerConfig.from(AppConfig(adBlocking = false, extraBlockedHosts = setOf("bad.com"), playerMode = PlayerMode.IFRAME))
        assertEquals(PlayerMode.IFRAME, noAds.mode)
        assertEquals(setOf("bad.com"), noAds.blockedHosts)
        assertTrue(PlayerConfig.from(AppConfig(adBlocking = true, extraBlockedHosts = setOf("bad.com"))).blockedHosts.containsAll(AdHosts.DEFAULT + "bad.com"))
    }

    @Test
    fun `pop-up switch is off unless a debug build sets it`() {
        assertFalse(PlayerUrlPolicy().dropPopups)
        assertEquals(BuildConfig.DROP_POPUPS, PlayerConfig.from(AppConfig.Default).dropPopups)
    }

    @Test
    fun `default ad list does not block scripts, owner's extra hosts do`() {
        // Blocking the ad scripts made the embed refuse to play ("remove sandbox attributes").
        val config = PlayerConfig.from(AppConfig(adBlocking = true, extraBlockedHosts = setOf("bad.com")))
        assertTrue(config.blockedResourceHosts == setOf("bad.com"))
        val policy = PlayerUrlPolicy(blockedHosts = config.blockedHosts, blockedResourceHosts = config.blockedResourceHosts)
        assertEquals(PlayerUrlPolicy.Decision.ALLOW, policy.resource("https://a.exosrv.com/ad-provider.js"))
        assertEquals(PlayerUrlPolicy.Decision.BLOCK, policy.resource("https://x.bad.com/a.js"))
        // Pop-ups and ad subframes are still refused.
        assertEquals(PlayerUrlPolicy.Decision.BLOCK, policy.navigation("https://ads.popads.net/r", isMainFrame = true))
        assertEquals(PlayerUrlPolicy.Decision.BLOCK, policy.navigation("https://a.exosrv.com/frame", isMainFrame = false))
    }
}
