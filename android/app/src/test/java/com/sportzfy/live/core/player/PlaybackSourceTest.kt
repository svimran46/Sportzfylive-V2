package com.sportzfy.live.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourceTest {

    @Test
    fun `m3u8 URLs use the HLS engine`() {
        assertEquals(PlaybackSource.Hls("https://cdn.example/live/index.m3u8"), PlaybackSource.from("https://cdn.example/live/index.m3u8"))
        assertEquals(
            PlaybackSource.Hls("https://cdn.example/a.M3U8?token=1#t"),
            PlaybackSource.from(" https://cdn.example/a.M3U8?token=1#t "),
        )
    }

    @Test
    fun `everything else uses the embed engine`() {
        assertEquals(PlaybackSource.Embed("https://embed.st/embed/a/b/1"), PlaybackSource.from("https://embed.st/embed/a/b/1"))
        assertEquals(PlaybackSource.Embed("https://x.example/p?f=a.m3u8"), PlaybackSource.from("https://x.example/p?f=a.m3u8"))
    }

    @Test
    fun `non-https URLs are rejected`() {
        assertNull(PlaybackSource.from("http://cdn.example/a.m3u8"))
        assertNull(PlaybackSource.from("intent://x"))
        assertNull(PlaybackSource.from(""))
    }
}
