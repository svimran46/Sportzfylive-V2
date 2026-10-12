package com.sportzfy.live.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserHeadersInterceptorTest {

    private fun send(request: Request): Request {
        var seen: Request? = null
        val capture = Interceptor { chain ->
            seen = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody())
                .build()
        }
        OkHttpClient.Builder()
            .addInterceptor(BrowserHeadersInterceptor())
            .addInterceptor(capture)
            .build()
            .newCall(request)
            .execute()
            .close()
        return seen!!
    }

    @Test
    fun `adds referer and user agent for streamed`() {
        val sent = send(Request.Builder().url("https://streamed.pk/api/matches/live").build())
        assertEquals("https://streamed.pk/", sent.header("Referer"))
        assertEquals(BrowserHeadersInterceptor.CHROME_ANDROID_USER_AGENT, sent.header("User-Agent"))
    }

    @Test
    fun `adds only the user agent for other hosts`() {
        val sent = send(Request.Builder().url("https://cdn.example/x.webp").build())
        assertNull(sent.header("Referer"))
        assertEquals(BrowserHeadersInterceptor.CHROME_ANDROID_USER_AGENT, sent.header("User-Agent"))
    }

    @Test
    fun `keeps headers the caller already set`() {
        val sent = send(
            Request.Builder().url("https://streamed.pk/x").header("Referer", "https://custom/").build()
        )
        assertEquals("https://custom/", sent.header("Referer"))
    }
}
