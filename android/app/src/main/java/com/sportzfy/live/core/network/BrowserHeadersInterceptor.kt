package com.sportzfy.live.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Makes requests look like Chrome on Android. streamed.pk sits behind
 * DDoS-Guard, which rejects requests without a browser User-Agent and a
 * same-site Referer. Headers a caller already set are left untouched.
 */
class BrowserHeadersInterceptor(
    private val userAgent: String = CHROME_ANDROID_USER_AGENT,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        if (request.header(USER_AGENT) == null) builder.header(USER_AGENT, userAgent)
        if (request.url.host == STREAMED_HOST) {
            if (request.header(REFERER) == null) builder.header(REFERER, StreamedEndpoints.REFERER)
            if (request.header(ACCEPT) == null) builder.header(ACCEPT, "application/json, image/webp, */*")
        }
        return chain.proceed(builder.build())
    }

    companion object {
        const val CHROME_ANDROID_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"

        private const val STREAMED_HOST = "streamed.pk"
        private const val USER_AGENT = "User-Agent"
        private const val REFERER = "Referer"
        private const val ACCEPT = "Accept"
    }
}
