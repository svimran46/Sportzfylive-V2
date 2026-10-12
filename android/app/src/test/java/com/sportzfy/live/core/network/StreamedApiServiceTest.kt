package com.sportzfy.live.core.network

import com.sportzfy.live.domain.model.DataError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.create

class StreamedApiServiceTest {

    private val server = MockWebServer()
    private lateinit var api: StreamedApiService

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(BrowserHeadersInterceptor()).build()
        api = HttpClientFactory.retrofit(client, NetworkJson, server.url("/").toString()).create()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `requests today's matches with a browser user agent`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id":"a"}]"""))

        val body = api.getMatchesToday()

        assertEquals(1, (body as JsonArray).size)
        val request = server.takeRequest()
        assertEquals("/api/matches/all-today", request.path)
        assertTrue(request.getHeader("User-Agent")!!.contains("Chrome/"))
        assertTrue(request.getHeader("User-Agent")!!.contains("Android"))
        // The mock server is not streamed.pk, so no Referer is attached.
        assertNull(request.getHeader("Referer"))
    }

    @Test
    fun `requests every listed match`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        api.getAllMatches()
        assertEquals("/api/matches/all", server.takeRequest().path)
    }

    @Test
    fun `requests the live feed`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        api.getLiveMatches()
        assertEquals("/api/matches/live", server.takeRequest().path)
    }

    @Test
    fun `encodes stream path segments and returns raw error responses`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        val response = api.getSourceStreams("alpha", "a/b c")

        assertEquals(429, response.code())
        assertEquals("/api/stream/alpha/a%2Fb%20c", server.takeRequest().path)
    }

    @Test
    fun `non-2xx match responses map to domain errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        try {
            api.getMatchesToday()
            fail("expected HttpException")
        } catch (e: HttpException) {
            assertEquals(DataError.BLOCKED, e.toDataError())
        }
    }
}
