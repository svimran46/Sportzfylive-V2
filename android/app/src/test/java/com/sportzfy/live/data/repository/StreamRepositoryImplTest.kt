package com.sportzfy.live.data.repository

import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.testing.FakeClock
import com.sportzfy.live.testing.FakeStreamedApi
import com.sportzfy.live.testing.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class StreamRepositoryImplTest {

    private val clock = FakeClock()
    private val api = FakeStreamedApi()
    private val repository = StreamRepositoryImpl(api, NetworkJson, clock, ttlMillis = 60_000L)

    private val alpha = StreamSource("alpha", "m-a")
    private val bravo = StreamSource("bravo", "m-b")
    private val charlie = StreamSource("charlie", "m-c")

    private fun ok(vararg numbers: Int) =
        Response.success(json(numbers.joinToString(",", "[", "]") { """{"streamNo":$it,"language":"English"}""" }))

    @Test
    fun `one failing source does not fail the others`() = runTest {
        api.streams = { source, _ ->
            when (source) {
                "alpha" -> ok(1, 2)
                "bravo" -> FakeStreamedApi.errorResponse(429)
                else -> throw IOException("timeout")
            }
        }

        val result = repository.resolveStreams(listOf(alpha, bravo, charlie))

        assertEquals(listOf("alpha/m-a/1", "alpha/m-a/2"), result.streams.map { it.key })
        assertEquals(listOf(bravo, charlie), result.failedSources)
    }

    @Test
    fun `404 means no streams rather than a failure`() = runTest {
        api.streams = { _, _ -> FakeStreamedApi.errorResponse(404) }
        val result = repository.resolveStreams(listOf(alpha))
        assertTrue(result.streams.isEmpty())
        assertTrue(result.failedSources.isEmpty())
    }

    @Test
    fun `caches each group for the TTL`() = runTest {
        api.streams = { _, _ -> ok(1) }
        repository.resolveStreams(listOf(alpha, bravo))
        clock.advance(30_000)
        repository.resolveStreams(listOf(alpha))
        assertEquals(listOf("alpha/m-a", "bravo/m-b"), api.streamCalls.sorted())

        clock.advance(31_000)
        repository.resolveStreams(listOf(alpha))
        assertEquals(3, api.streamCalls.size)
    }

    @Test
    fun `serves stale streams when a refresh fails`() = runTest {
        api.streams = { _, _ -> ok(1, 2) }
        repository.resolveStreams(listOf(alpha))

        clock.advance(120_000)
        api.streams = { _, _ -> FakeStreamedApi.errorResponse(503) }
        val result = repository.resolveStreams(listOf(alpha))

        assertEquals(2, result.streams.size)
        assertTrue(result.failedSources.isEmpty())
    }

    @Test
    fun `duplicate groups are resolved once`() = runTest {
        repository.resolveStreams(listOf(alpha, alpha))
        assertEquals(1, api.streamCalls.size)
    }
}
