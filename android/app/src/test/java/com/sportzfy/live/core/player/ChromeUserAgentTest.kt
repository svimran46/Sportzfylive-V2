package com.sportzfy.live.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ChromeUserAgentTest {

    @Test
    fun `phone WebView becomes Chrome's reduced user agent`() {
        val webView = "Mozilla/5.0 (Linux; Android 16; A059 Build/BQ2A.250721.001; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/153.0.8010.39 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36",
            ChromeUserAgent.from(webView),
        )
    }

    @Test
    fun `tablet keeps no Mobile token`() {
        val webView = "Mozilla/5.0 (Linux; Android 14; SM-X200 Build/UP1A; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.51 Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
            ChromeUserAgent.from(webView),
        )
    }

    @Test
    fun `unknown engine only loses the WebView marker`() {
        assertEquals("Mozilla/5.0 (Linux; Android 9)", ChromeUserAgent.from("Mozilla/5.0 (Linux; Android 9; wv)"))
    }
}
