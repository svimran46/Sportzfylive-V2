package com.sportzfy.live.core.player

/**
 * Hosts an embed inside a plain iframe, matching the website's player: no
 * `sandbox` attribute (embeds refuse to play in one: "Remove sandbox
 * attributes on the iframe tag"), full autoplay/fullscreen permissions and
 * no referrer. The app still owns the top-level page, so pop-ups and
 * `top.location` hijacks are refused by the WebView instead.
 */
object EmbedFrame {
    /** Origin the wrapper page runs under; mirrors the official embedding site. */
    const val BASE_URL = "https://streamed.pk/"

    fun html(embedUrl: String): String =
        """<!doctype html><html><head><meta charset="utf-8">""" +
            """<meta name="viewport" content="width=device-width,initial-scale=1">""" +
            """<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}""" +
            """iframe{display:block;border:0;width:100%;height:100%}</style></head><body>""" +
            """<iframe src="${escapeAttribute(embedUrl)}" """ +
            """allow="autoplay; fullscreen; encrypted-media; picture-in-picture" """ +
            """allowfullscreen referrerpolicy="no-referrer"></iframe></body></html>"""

    private fun escapeAttribute(value: String): String =
        value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
