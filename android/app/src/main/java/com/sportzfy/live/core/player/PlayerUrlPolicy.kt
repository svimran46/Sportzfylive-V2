package com.sportzfy.live.core.player

import com.sportzfy.live.domain.model.EmbedUrl

/**
 * Decides what the embed WebView may navigate to and load, for any stream
 * URL the app plays.
 *
 * - The top-level page stays on the active stream's host ([streamUrl]), on
 *   [trustedHosts], or on the host its first load landed on after server
 *   redirects. Ads hijack the page with `top.location` or a pop-up; with
 *   pop-ups trapped by the WebView, both arrive here as main-frame
 *   navigations to another site and are cancelled.
 * - Subframes always load, whatever their host or scheme: embeds nest their
 *   real player in cross-domain iframes, and refusing one leaves it black.
 * - At the top level, other schemes (`intent:`, `market:`, `tel:`, `file:`,
 *   `data:` and so on) never load; `about:blank` does.
 * - Sub-resources (scripts, XHR, images) from [blockedResourceHosts] are
 *   refused; the WebView answers them with an empty success.
 */
class PlayerUrlPolicy(
    streamUrl: String? = null,
    private val trustedHosts: Set<String> = setOf(EmbedUrl.HOST),
    private val blockedHosts: Set<String> = AdHosts.DEFAULT,
    private val blockedResourceHosts: Set<String> = emptySet(),
) {

    enum class Decision { ALLOW, BLOCK }

    private val streamDomain: String? = streamUrl?.let(::hostOf)?.let(::siteDomain)

    /**
     * [pageHost] is the host the player's page landed on, and [landing] is
     * true while that first load is still following server redirects.
     */
    fun navigation(
        url: String,
        isMainFrame: Boolean,
        isRedirect: Boolean = false,
        pageHost: String? = null,
        landing: Boolean = false,
    ): Decision {
        if (!isMainFrame) return Decision.ALLOW
        val parsed = parse(url) ?: return Decision.BLOCK
        return when (parsed.scheme) {
            "about" -> if (parsed.rest == "blank" || parsed.rest == "srcdoc") Decision.ALLOW else Decision.BLOCK
            "http", "https" -> {
                val host = parsed.host
                when {
                    host == null || host.matchesAny(blockedHosts) -> Decision.BLOCK
                    // The stream's own server may send its first load elsewhere (a mirror, a CDN page).
                    landing && isRedirect -> Decision.ALLOW
                    host.matchesAny(mainFrameDomains(pageHost)) -> Decision.ALLOW
                    else -> Decision.BLOCK
                }
            }
            else -> Decision.BLOCK
        }
    }

    fun resource(url: String): Decision {
        val host = parse(url)?.host ?: return Decision.ALLOW
        return if (host.matchesAny(blockedResourceHosts)) Decision.BLOCK else Decision.ALLOW
    }

    private fun mainFrameDomains(pageHost: String?): Set<String> =
        trustedHosts + listOfNotNull(streamDomain, pageHost?.let(::siteDomain))

    private class ParsedUrl(val scheme: String, val host: String?, val rest: String)

    private fun hostOf(url: String): String? = parse(url)?.host

    /** `www.` is dropped so the bare domain and its other subdomains count as the same site. */
    private fun siteDomain(host: String): String = host.lowercase().trimEnd('.').removePrefix("www.")

    private fun parse(url: String): ParsedUrl? {
        val trimmed = url.trim()
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val scheme = trimmed.substring(0, colon).lowercase()
        if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
        val rest = trimmed.substring(colon + 1)
        if (!rest.startsWith("//")) return ParsedUrl(scheme, host = null, rest = rest)

        // authority = [userinfo@]host[:port], ending at the first / ? or #
        val authority = rest.substring(2).takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
        val hostPort = authority.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringBefore(']') + "]"
        } else {
            hostPort.substringBefore(':')
        }
        return ParsedUrl(scheme, host.lowercase().trimEnd('.').ifEmpty { null }, rest)
    }

    private fun String.matchesAny(domains: Set<String>): Boolean =
        domains.any { domain -> this == domain || endsWith(".$domain") }
}
