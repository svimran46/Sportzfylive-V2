package com.sportzfy.live.core.player

import com.sportzfy.live.domain.model.EmbedUrl

/**
 * Decides what the embed WebView may navigate to and load.
 *
 * - Top-level navigations stay on [trustedHosts]. Ads open pop-ups through
 *   `window.open` or `top.location`. With multiple windows disabled, both
 *   arrive here as main-frame navigations and are dropped. The exception is
 *   an HTTP redirect answering a navigation that was already allowed.
 * - Subframes may load any https page except [blockedHosts], because the
 *   embed nests its real player in a third-party iframe.
 * - Any scheme other than https is refused (`intent:`, `market:`, `tel:`,
 *   plain http and so on). The only exceptions are `about:blank` and
 *   `about:srcdoc`.
 * - Sub-resources (scripts, XHR, images) from [blockedResourceHosts] are
 *   refused. By default that is [blockedHosts]; the app narrows it so the
 *   ad list stops pop-ups and redirects without starving the embed's own
 *   scripts, whose failure makes the stream page refuse to play.
 */
class PlayerUrlPolicy(
    private val trustedHosts: Set<String> = setOf(EmbedUrl.HOST),
    private val blockedHosts: Set<String> = AdHosts.DEFAULT,
    private val blockedResourceHosts: Set<String> = blockedHosts,
    /**
     * Browser-like mode, used by the app: stream sites are paid for by ads
     * and only play once the viewer has been sent to them. The page may go
     * anywhere on the web (http or https, except [blockedHosts]); links for
     * other apps (`intent:`, `market:` and the like) are handed to Android.
     */
    private val openWeb: Boolean = false,
) {

    /** [EXTERNAL]: open with another app (browser, Play Store) instead of the player. */
    enum class Decision { ALLOW, BLOCK, EXTERNAL }

    fun navigation(url: String, isMainFrame: Boolean, isRedirect: Boolean = false): Decision {
        val parsed = parse(url) ?: return Decision.BLOCK
        if (parsed.scheme == "about") {
            return if (parsed.rest == "blank" || parsed.rest == "srcdoc") Decision.ALLOW else Decision.BLOCK
        }
        if (openWeb) return openNavigation(parsed, isMainFrame)
        val host = parsed.host
        if (parsed.scheme != "https" || host == null) return Decision.BLOCK
        if (host.matchesAny(blockedHosts)) return Decision.BLOCK
        if (!isMainFrame || isRedirect) return Decision.ALLOW
        return if (host.matchesAny(trustedHosts)) Decision.ALLOW else Decision.BLOCK
    }

    private fun openNavigation(parsed: ParsedUrl, isMainFrame: Boolean): Decision = when (parsed.scheme) {
        "http", "https" -> {
            val host = parsed.host
            if (host == null || host.matchesAny(blockedHosts)) Decision.BLOCK else Decision.ALLOW
        }
        "data", "blob" -> if (isMainFrame) Decision.BLOCK else Decision.ALLOW
        // Files and app-internal schemes never enter the player.
        "file", "content", "javascript" -> Decision.BLOCK
        else -> if (isMainFrame) Decision.EXTERNAL else Decision.BLOCK
    }

    fun resource(url: String): Decision {
        val host = parse(url)?.host ?: return Decision.ALLOW
        return if (host.matchesAny(blockedResourceHosts)) Decision.BLOCK else Decision.ALLOW
    }

    private class ParsedUrl(val scheme: String, val host: String?, val rest: String)

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
