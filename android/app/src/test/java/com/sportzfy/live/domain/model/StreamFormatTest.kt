package com.sportzfy.live.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamFormatTest {

    @Test
    fun `guesses from the last path segment's extension`() {
        assertEquals(StreamFormat.HLS, StreamFormat.fromUrl("https://cdn.example/live/index.m3u8"))
        assertEquals(StreamFormat.HLS, StreamFormat.fromUrl("https://cdn.example/live/INDEX.M3U8?token=a.mpd#x.ts"))
        assertEquals(StreamFormat.DASH, StreamFormat.fromUrl("https://cdn.example/stream/manifest.mpd"))
        assertEquals(StreamFormat.PROGRESSIVE, StreamFormat.fromUrl("http://iptv.example:8080/live/u/p/42.ts"))
        assertEquals(StreamFormat.PROGRESSIVE, StreamFormat.fromUrl("https://cdn.example/v/clip.mp4"))
        assertEquals(StreamFormat.PROGRESSIVE, StreamFormat.fromUrl("https://cdn.example/radio.aac"))
    }

    @Test
    fun `pages, folders and extension-like hosts are embeds`() {
        assertEquals(StreamFormat.EMBED, StreamFormat.fromUrl("https://embed.st/embed/alpha/match-101/1"))
        assertEquals(StreamFormat.EMBED, StreamFormat.fromUrl("https://tv.example/watch.php?file=a.m3u8"))
        assertEquals(StreamFormat.EMBED, StreamFormat.fromUrl("https://cdn.example/a.m3u8/"))
        assertEquals(StreamFormat.EMBED, StreamFormat.fromUrl("https://stream.ts"))
        assertEquals(StreamFormat.EMBED, StreamFormat.fromUrl("https://stream.ts?x=1"))
        assertEquals(StreamFormat.EMBED, StreamFormat.fromUrl("https://tv.example/channel"))
    }

    @Test
    fun `reads type hints loosely`() {
        assertEquals(StreamFormat.HLS, StreamFormat.fromHint(" HLS "))
        assertEquals(StreamFormat.HLS, StreamFormat.fromHint("application/vnd.apple.mpegurl"))
        assertEquals(StreamFormat.DASH, StreamFormat.fromHint("mpd"))
        assertEquals(StreamFormat.PROGRESSIVE, StreamFormat.fromHint("ts"))
        assertEquals(StreamFormat.PROGRESSIVE, StreamFormat.fromHint("video/mp4"))
        assertEquals(StreamFormat.EMBED, StreamFormat.fromHint("iframe"))
        assertNull(StreamFormat.fromHint(null))
        assertNull(StreamFormat.fromHint(""))
        assertNull(StreamFormat.fromHint("rtmp"))
    }
}
