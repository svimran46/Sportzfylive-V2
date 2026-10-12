package com.sportzfy.live.core.player

/** Which engine plays a URL: Media3 for native HLS, the sandboxed WebView otherwise. */
sealed interface PlaybackSource {
    val url: String

    data class Hls(override val url: String) : PlaybackSource

    data class Embed(override val url: String) : PlaybackSource

    companion object {
        /** Returns null for anything other than an https URL. */
        fun from(url: String): PlaybackSource? {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return null
            val path = trimmed.substringBefore('#').substringBefore('?')
            return if (path.endsWith(".m3u8", ignoreCase = true)) Hls(trimmed) else Embed(trimmed)
        }
    }
}
