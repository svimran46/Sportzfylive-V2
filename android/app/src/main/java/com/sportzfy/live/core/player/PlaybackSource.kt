package com.sportzfy.live.core.player

import com.sportzfy.live.domain.model.StreamFormat

/** Which engine plays a URL: Media3 for media streams, the WebView for web pages. */
sealed interface PlaybackSource {
    val url: String

    /** HLS, DASH or a progressive file/stream, played by Media3. Never [StreamFormat.EMBED]. */
    data class Media(override val url: String, val format: StreamFormat) : PlaybackSource

    data class Embed(override val url: String) : PlaybackSource

    companion object {
        /**
         * Picks the engine for [url]. [hint] (a channel's `type`) wins over the
         * URL's extension. Returns null for anything that isn't an http(s) URL,
         * for plain http when [allowHttp] is off, and for DASH or progressive
         * streams when [extendedFormats] is off (HLS and web pages always play).
         */
        fun from(
            url: String,
            hint: StreamFormat? = null,
            allowHttp: Boolean = true,
            extendedFormats: Boolean = true,
        ): PlaybackSource? {
            val trimmed = url.trim()
            val https = trimmed.startsWith("https://", ignoreCase = true)
            val http = trimmed.startsWith("http://", ignoreCase = true)
            if (!https && !(http && allowHttp)) return null
            val host = trimmed.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            if (host.isEmpty() || trimmed.any(Char::isWhitespace)) return null
            return when (val format = hint ?: StreamFormat.fromUrl(trimmed)) {
                StreamFormat.EMBED -> Embed(trimmed)
                StreamFormat.HLS -> Media(trimmed, format)
                StreamFormat.DASH, StreamFormat.PROGRESSIVE -> if (extendedFormats) Media(trimmed, format) else null
            }
        }
    }
}
