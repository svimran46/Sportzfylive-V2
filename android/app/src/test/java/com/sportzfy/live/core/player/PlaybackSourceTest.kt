package com.sportzfy.live.core.player

import com.sportzfy.live.domain.model.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourceTest {

    @Test
    fun `m3u8 URLs use Media3 as HLS`() {
        assertEquals(
            PlaybackSource.Media("https://cdn.example/live/index.m3u8", StreamFormat.HLS),
            PlaybackSource.from("https://cdn.example/live/index.m3u8"),
        )
        assertEquals(
            PlaybackSource.Media("https://cdn.example/a.M3U8?token=1#t", StreamFormat.HLS),
            PlaybackSource.from(" https://cdn.example/a.M3U8?token=1#t "),
        )
    }

    @Test
    fun `mpd URLs use Media3 as DASH`() {
        assertEquals(
            PlaybackSource.Media("https://cdn.example/live/manifest.mpd?x=1", StreamFormat.DASH),
            PlaybackSource.from("https://cdn.example/live/manifest.mpd?x=1"),
        )
    }

    @Test
    fun `ts and other media files use Media3 as progressive`() {
        for (url in listOf("http://iptv.example:8080/live/1.ts", "https://cdn.example/clip.mp4", "https://cdn.example/a.MKV")) {
            assertEquals(PlaybackSource.Media(url, StreamFormat.PROGRESSIVE), PlaybackSource.from(url))
        }
    }

    @Test
    fun `everything else uses the embed engine`() {
        assertEquals(PlaybackSource.Embed("https://embed.st/embed/a/b/1"), PlaybackSource.from("https://embed.st/embed/a/b/1"))
        assertEquals(PlaybackSource.Embed("https://x.example/p?f=a.m3u8"), PlaybackSource.from("https://x.example/p?f=a.m3u8"))
        // A host name that merely ends like an extension is still a page.
        assertEquals(PlaybackSource.Embed("https://stream.ts"), PlaybackSource.from("https://stream.ts"))
    }

    @Test
    fun `a type hint wins over the URL`() {
        assertEquals(
            PlaybackSource.Media("http://iptv.example:8080/user/pass/12345", StreamFormat.PROGRESSIVE),
            PlaybackSource.from("http://iptv.example:8080/user/pass/12345", hint = StreamFormat.PROGRESSIVE),
        )
        assertEquals(
            PlaybackSource.Media("https://cdn.example/play?id=7", StreamFormat.HLS),
            PlaybackSource.from("https://cdn.example/play?id=7", hint = StreamFormat.HLS),
        )
        assertEquals(PlaybackSource.Embed("https://cdn.example/a.m3u8"), PlaybackSource.from("https://cdn.example/a.m3u8", hint = StreamFormat.EMBED))
    }

    @Test
    fun `plain http plays unless it is turned off`() {
        assertEquals(
            PlaybackSource.Media("http://cdn.example/a.m3u8", StreamFormat.HLS),
            PlaybackSource.from("http://cdn.example/a.m3u8"),
        )
        assertEquals(PlaybackSource.Embed("http://tv.example/channel"), PlaybackSource.from("http://tv.example/channel"))
        assertNull(PlaybackSource.from("http://cdn.example/a.m3u8", allowHttp = false))
        assertNull(PlaybackSource.from("http://tv.example/channel", allowHttp = false))
        assertEquals(
            PlaybackSource.Media("https://cdn.example/a.m3u8", StreamFormat.HLS),
            PlaybackSource.from("https://cdn.example/a.m3u8", allowHttp = false),
        )
    }

    @Test
    fun `extended formats can be turned off, leaving HLS and pages`() {
        assertNull(PlaybackSource.from("https://cdn.example/a.mpd", extendedFormats = false))
        assertNull(PlaybackSource.from("https://cdn.example/a.ts", extendedFormats = false))
        assertEquals(
            PlaybackSource.Media("https://cdn.example/a.m3u8", StreamFormat.HLS),
            PlaybackSource.from("https://cdn.example/a.m3u8", extendedFormats = false),
        )
        assertEquals(PlaybackSource.Embed("https://embed.st/embed/a/b/1"), PlaybackSource.from("https://embed.st/embed/a/b/1", extendedFormats = false))
    }

    @Test
    fun `anything that is not an http URL is rejected`() {
        assertNull(PlaybackSource.from("intent://x"))
        assertNull(PlaybackSource.from("file:///sdcard/a.mp4"))
        assertNull(PlaybackSource.from("javascript:alert(1)"))
        assertNull(PlaybackSource.from(""))
        assertNull(PlaybackSource.from("https://"))
        assertNull(PlaybackSource.from("https://cdn.example/a b.m3u8"))
    }
}
