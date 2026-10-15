package com.sportzfy.live.domain.model

import java.net.URLEncoder

/**
 * Builds playback URLs from the deterministic Streamed embed pattern
 * `https://embed.st/embed/{source}/{id}/{streamNo}`, mirroring the web app.
 * Upstream `embedUrl` fields are never trusted.
 */
object EmbedUrl {
    const val HOST = "embed.st"
    const val BASE = "https://$HOST/embed"

    fun build(source: String, id: String, streamNo: Int): String {
        require(source.isNotBlank()) { "source must not be blank" }
        require(id.isNotBlank()) { "id must not be blank" }
        require(streamNo > 0) { "streamNo must be positive" }
        return "$BASE/${encodeSegment(source)}/${encodeSegment(id)}/$streamNo"
    }

    // URLEncoder targets form encoding; a path segment needs %20, not '+'.
    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
