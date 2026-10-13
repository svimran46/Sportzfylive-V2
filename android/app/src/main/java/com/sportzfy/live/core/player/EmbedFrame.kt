package com.sportzfy.live.core.player

/**
 * Hosts an embed inside a plain iframe, matching the website's player: no
 * `sandbox` attribute, full autoplay/fullscreen permissions and no referrer.
 * Embed pages that expect to run framed (or that redirect themselves in
 * script) play normally, while the app still owns the top-level page, so
 * pop-ups and `top.location` hijacks can be refused.
 */
object EmbedFrame {
    /** Origin the wrapper page runs under; mirrors the official embedding site. */
    const val BASE_URL = "https://streamed.pk/"

    /**
     * Sandbox for a framed channel page. Scripts and same-origin are allowed so
     * the player runs. Pop-ups and top-level navigation are not, so the frame
     * cannot open windows or move the app's page. Same-origin is safe here
     * because the page is cross-origin to the wrapper.
     */
    const val SANDBOX = "allow-scripts allow-same-origin allow-forms allow-presentation"

    /** [sandbox] adds a `sandbox` attribute to the iframe; null leaves it off. */
    fun html(embedUrl: String, sandbox: String? = null): String {
        val sandboxAttribute = sandbox?.let { """sandbox="${escapeAttribute(it)}" """ }.orEmpty()
        return """<!doctype html><html><head><meta charset="utf-8">""" +
            """<meta name="viewport" content="width=device-width,initial-scale=1">""" +
            """<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}""" +
            """iframe{display:block;border:0;width:100%;height:100%}</style></head><body>""" +
            """<iframe src="${escapeAttribute(embedUrl)}" """ +
            sandboxAttribute +
            """allow="autoplay; fullscreen; encrypted-media; picture-in-picture" """ +
            """allowfullscreen referrerpolicy="no-referrer"></iframe></body></html>"""
    }

    private fun escapeAttribute(value: String): String =
        value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
