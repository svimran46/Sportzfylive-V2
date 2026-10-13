package com.sportzfy.live.core.player

import com.sportzfy.live.core.player.PlayerUrlPolicy.Decision.ALLOW
import com.sportzfy.live.core.player.PlayerUrlPolicy.Decision.BLOCK
import com.sportzfy.live.core.player.PlayerUrlPolicy.Decision.EXTERNAL
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerUrlPolicyTest {

    private val policy = PlayerUrlPolicy()

    private fun main(url: String, redirect: Boolean = false) = policy.navigation(url, isMainFrame = true, isRedirect = redirect)
    private fun sub(url: String) = policy.navigation(url, isMainFrame = false)

    @Test
    fun `main frame stays on embed st`() {
        assertEquals(ALLOW, main("https://embed.st/embed/alpha/match-101/1"))
        assertEquals(ALLOW, main("https://cdn.embed.st/x"))
        assertEquals(ALLOW, main("HTTPS://EMBED.ST./embed/a/b/1"))
        assertEquals(ALLOW, main("https://embed.st:443/embed/a/b/1"))
    }

    @Test
    fun `main frame pop-ups and lookalike hosts are blocked`() {
        assertEquals(BLOCK, main("https://bet-now.example/promo"))
        assertEquals(BLOCK, main("https://embed.st.evil.com/x"))
        assertEquals(BLOCK, main("https://evilembed.st/x"))
        assertEquals(BLOCK, main("https://embed.st@evil.com/x"))
        assertEquals(BLOCK, main("https://evil.com\\@embed.st/x"))
        assertEquals(BLOCK, main("https://evil.com?embed.st"))
    }

    @Test
    fun `non-https schemes are blocked everywhere`() {
        for (url in listOf(
            "intent://scan/#Intent;scheme=market;end",
            "market://details?id=com.casino",
            "tel:123",
            "mailto:a@b.c",
            "http://embed.st/embed/a/b/1",
            "javascript:alert(1)",
            "file:///sdcard/x",
            "data:text/html,<script>",
            "about:settings",
            "no-scheme",
            "",
        )) {
            assertEquals(url, BLOCK, main(url))
            assertEquals(url, BLOCK, sub(url))
        }
    }

    @Test
    fun `blank frames are allowed`() {
        assertEquals(ALLOW, main("about:blank"))
        assertEquals(ALLOW, sub("about:srcdoc"))
    }

    @Test
    fun `server redirects of an allowed navigation pass unless blocklisted`() {
        assertEquals(ALLOW, main("https://player.example/live/1", redirect = true))
        assertEquals(BLOCK, main("https://ads.popads.net/r", redirect = true))
    }

    @Test
    fun `subframes may load third-party players but not ad networks`() {
        assertEquals(ALLOW, sub("https://player.example/embed/1"))
        assertEquals(BLOCK, sub("https://googleads.g.doubleclick.net/pagead"))
        assertEquals(BLOCK, sub("https://www.propellerads.com/x"))
    }

    @Test
    fun `resources from ad hosts are blocked`() {
        assertEquals(BLOCK, policy.resource("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertEquals(BLOCK, policy.resource("https://a.exosrv.com/ad-provider.js"))
        assertEquals(ALLOW, policy.resource("https://cdn.example/hls/master.m3u8"))
        assertEquals(ALLOW, policy.resource("data:image/png;base64,AAAA"))
    }

    @Test
    fun `resource blocking can be narrower than navigation blocking`() {
        val narrow = PlayerUrlPolicy(blockedHosts = AdHosts.DEFAULT, blockedResourceHosts = setOf("bad.test"))
        assertEquals(ALLOW, narrow.resource("https://a.exosrv.com/ad-provider.js"))
        assertEquals(BLOCK, narrow.resource("https://x.bad.test/a.js"))
        assertEquals(BLOCK, narrow.navigation("https://ads.popads.net/r", isMainFrame = true))
        assertEquals(BLOCK, narrow.navigation("https://a.exosrv.com/frame", isMainFrame = false))
    }

    @Test
    fun `open web mode browses like a browser so the sites' ads work`() {
        val open = PlayerUrlPolicy(blockedHosts = emptySet(), openWeb = true)
        assertEquals(ALLOW, open.navigation("https://ads.example/landing", isMainFrame = true))
        assertEquals(ALLOW, open.navigation("http://player.example/live/1", isMainFrame = true))
        assertEquals(EXTERNAL, open.navigation("intent://app#Intent;package=x;end", isMainFrame = true))
        assertEquals(EXTERNAL, open.navigation("market://details?id=x", isMainFrame = true))
        assertEquals(BLOCK, open.navigation("file:///sdcard/x", isMainFrame = true))
        assertEquals(BLOCK, open.navigation("javascript:alert(1)", isMainFrame = true))
        // A host the owner blocks remotely stays blocked.
        val ownerBlocked = PlayerUrlPolicy(blockedHosts = setOf("bad.example"), openWeb = true)
        assertEquals(BLOCK, ownerBlocked.navigation("https://bad.example/x", isMainFrame = true))
    }

    @Test
    fun `trusted and blocked hosts are configurable`() {
        val custom = PlayerUrlPolicy(trustedHosts = setOf("player.test"), blockedHosts = setOf("bad.test"))
        assertEquals(ALLOW, custom.navigation("https://player.test/x", isMainFrame = true))
        assertEquals(BLOCK, custom.navigation("https://embed.st/x", isMainFrame = true))
        assertEquals(BLOCK, custom.resource("https://x.bad.test/a.js"))
    }
}
