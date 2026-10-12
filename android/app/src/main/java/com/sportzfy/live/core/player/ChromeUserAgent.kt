package com.sportzfy.live.core.player

/**
 * The user agent the player presents: exactly what Chrome on Android sends.
 * Stream sites refuse to play in an in-app WebView ("Remove sandbox
 * attributes on the iframe tag"), and they spot one by the `; wv` and
 * `Version/4.0` tokens and the device `Build/` id, none of which Chrome sends.
 */
object ChromeUserAgent {

    private val CHROME_MAJOR = Regex("""Chrome/(\d+)""")

    /** Returns Chrome's reduced user agent for [webViewUserAgent]'s engine version. */
    fun from(webViewUserAgent: String): String {
        val major = CHROME_MAJOR.find(webViewUserAgent)?.groupValues?.get(1)
            ?: return webViewUserAgent.replace("; wv)", ")")
        // Chrome leaves out "Mobile" on tablets, as WebView does.
        val mobile = if (" Mobile " in webViewUserAgent) " Mobile" else ""
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0$mobile Safari/537.36"
    }

    /** WebView's brand in `navigator.userAgentData`; Chrome reports [CHROME_BRAND] instead. */
    const val WEBVIEW_BRAND = "Android WebView"
    const val CHROME_BRAND = "Google Chrome"
}
