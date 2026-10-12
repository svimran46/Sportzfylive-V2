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
 * - Sub-resources (scripts, XHR, images) from [blockedHosts] are refused.
 */
class PlayerUrlPolicy(
    private val trustedHosts: Set<String> = setOf(EmbedUrl.HOST),
    private val blockedHosts: Set<String> = AdHosts.DEFAULT,
) {

    enum class Decision { ALLOW, BLOCK }

    fun navigation(url: String, isMainFrame: Boolean, isRedirect: Boolean = false): Decision {
        val parsed = parse(url) ?: return Decision.BLOCK
        if (parsed.scheme == "about") {
            return if (parsed.rest == "blank" || parsed.rest == "srcdoc") Decision.ALLOW else Decision.BLOCK
        }
        val host = parsed.host
        if (parsed.scheme != "https" || host == null) return Decision.BLOCK
        if (host.matchesAny(blockedHosts)) return Decision.BLOCK
        if (!isMainFrame || isRedirect) return Decision.ALLOW
        return if (host.matchesAny(trustedHosts)) Decision.ALLOW else Decision.BLOCK
    }

    fun resource(url: String): Decision {
        val host = parse(url)?.host ?: return Decision.ALLOW
        return if (host.matchesAny(blockedHosts)) Decision.BLOCK else Decision.ALLOW
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
