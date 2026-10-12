package com.sportzfy.live.core.network

/** Upstream hosts and paths consumed directly by the app (no proxy worker). */
object StreamedEndpoints {
    const val BASE_URL = "https://streamed.pk/"
    const val REFERER = "https://streamed.pk/"

    const val MATCHES_TODAY = "api/matches/all-today"
    const val MATCHES_LIVE = "api/matches/live"
    const val STREAM = "api/stream/{source}/{id}"

    private const val BADGE_PREFIX = "${BASE_URL}api/images/badge/"

    /** Resolves a poster path or absolute URL to an absolute https URL. */
    fun posterUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return when {
            value.startsWith("https://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> BASE_URL.dropLast(1) + value
            else -> null
        }
    }

    /** Resolves a team badge token or absolute URL to an absolute https URL. */
    fun badgeUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return when {
            value.startsWith("https://") -> value
            value.startsWith("//") -> "https:$value"
            else -> BADGE_PREFIX + java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20") + ".webp"
        }
    }
}
