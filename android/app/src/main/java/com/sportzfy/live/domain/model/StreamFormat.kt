package com.sportzfy.live.domain.model

/** How a stream URL is played: by Media3 (the first three) or as a web page in the WebView. */
enum class StreamFormat {
    /** HLS playlist (`.m3u8`). */
    HLS,

    /** MPEG-DASH manifest (`.mpd`). */
    DASH,

    /**
     * One media file or a continuous stream that Media3 reads directly:
     * MPEG-TS (raw IPTV feeds), MP4, Matroska/WebM, FLV, MP3 or AAC.
     */
    PROGRESSIVE,

    /** A web page (the `embed.st` players, channel sites). */
    EMBED,
    ;

    companion object {
        private val PROGRESSIVE_EXTENSIONS = setOf(
            "ts", "mts", "m2ts", "mp4", "m4v", "mkv", "webm", "mov", "flv", "mp3", "aac", "m4a",
        )

        /**
         * Reads a `type` hint from a channel list (`"hls"`, `"dash"`, `"ts"`, `"embed"`,
         * a MIME type…). Null for a blank or unknown hint, so the URL decides.
         */
        fun fromHint(hint: String?): StreamFormat? = when (hint?.trim()?.lowercase()) {
            "hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> HLS
            "dash", "mpd", "application/dash+xml" -> DASH
            "ts", "mpegts", "mpeg-ts", "mp4", "mkv", "webm", "flv", "mp3", "aac",
            "progressive", "file", "video", "video/mp2t", "video/mp4", "video/webm", "video/x-matroska" -> PROGRESSIVE
            "embed", "web", "page", "iframe", "html", "text/html" -> EMBED
            else -> null
        }

        /**
         * Guesses from the extension of the URL's last path segment (query and
         * fragment ignored). Anything without a media extension is a web page.
         */
        fun fromUrl(url: String): StreamFormat {
            val path = url.substringBefore('#').substringBefore('?')
            val afterHost = path.substringAfter("://", path).substringAfter('/', "")
            val extension = afterHost.substringAfterLast('/').substringAfterLast('.', "").lowercase()
            return when (extension) {
                "m3u8" -> HLS
                "mpd" -> DASH
                in PROGRESSIVE_EXTENSIONS -> PROGRESSIVE
                else -> EMBED
            }
        }
    }
}
