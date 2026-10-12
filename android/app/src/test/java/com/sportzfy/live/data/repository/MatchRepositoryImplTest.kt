package com.sportzfy.live.data.repository

import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.domain.model.DataError
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.testing.FakeClock
import com.sportzfy.live.testing.FakeStreamedApi
import com.sportzfy.live.testing.httpError
import com.sportzfy.live.testing.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchRepositoryImplTest {

    private val clock = FakeClock()
    private val hour = 60 * 60 * 1000L
    private val api = FakeStreamedApi()
    private val repository = MatchRepositoryImpl(api, NetworkJson, clock, ttlMillis = 60_000L)

    private fun match(id: String, offset: Long) = """{"id":"$id","title":"$id","date":${clock.now + offset}}"""

    @Test
    fun `merges today and live into the feed`() = runTest {
        api.today = { json("""[${match("a", -hour)}, ${match("b", hour)}]""") }
        api.live = { json("""[${match("a", -hour)}]""") }

        repository.refresh()

        val feed = repository.feed.value
        assertEquals(listOf("a", "b"), feed.matches.map { it.id })
        assertEquals(MatchStatus.LIVE, feed.matches.first().status)
        assertEquals(1, feed.liveCount)
        assertEquals(clock.now, feed.updatedAtMillis)
        assertFalse(feed.isLoading)
        assertNull(feed.error)
    }

    @Test
    fun `serves from cache within the TTL and refetches after it`() = runTest {
        repository.refresh()
        clock.advance(59_000)
        repository.refresh()
        assertEquals(1, api.todayCalls)

        clock.advance(2_000)
        repository.refresh()
        assertEquals(2, api.todayCalls)
    }

    @Test
    fun `force bypasses the TTL`() = runTest {
        repository.refresh()
        repository.refresh(force = true)
        assertEquals(2, api.todayCalls)
    }

    @Test
    fun `concurrent refreshes share one upstream call`() = runTest {
        List(5) { async { repository.refresh() } }.awaitAll()
        assertEquals(1, api.todayCalls)
        assertEquals(1, api.liveCalls)
    }

    @Test
    fun `live failure never marks a match live`() = runTest {
        api.today = { json("""[${match("recent", -hour)}, ${match("old", -5 * hour)}]""") }
        api.live = { throw httpError(503) }

        repository.refresh()

        val feed = repository.feed.value
        val byId = feed.matches.associateBy { it.id }
        assertEquals(MatchStatus.STARTED, byId.getValue("recent").status)
        assertEquals(MatchStatus.STARTED, byId.getValue("old").status)
        assertEquals(0, feed.liveCount)
        assertFalse(feed.liveStatusKnown)
        assertNull(feed.error)
    }

    @Test
    fun `a recent live payload stands in for a failed live feed`() = runTest {
        api.today = { json("""[${match("a", -hour)}]""") }
        api.live = { json("""[${match("a", -hour)}]""") }
        repository.refresh()

        api.live = { throw httpError(503) }
        clock.advance(61_000)
        repository.refresh()
        assertEquals(MatchStatus.LIVE, repository.feed.value.matches.single().status)
        assertTrue(repository.feed.value.liveStatusKnown)

        clock.advance(MatchRepositoryImpl.LIVE_REUSE_MILLIS)
        repository.refresh()
        assertEquals(MatchStatus.STARTED, repository.feed.value.matches.single().status)
        assertFalse(repository.feed.value.liveStatusKnown)
    }

    @Test
    fun `going offline drops LIVE badges once the live list is too old to trust`() = runTest {
        api.today = { json("""[${match("a", -hour)}]""") }
        api.live = { json("""[${match("a", -hour)}]""") }
        repository.refresh()
        assertEquals(MatchStatus.LIVE, repository.feed.value.matches.single().status)

        api.today = { FakeStreamedApi.networkDown() }
        api.all = { FakeStreamedApi.networkDown() }
        api.live = { FakeStreamedApi.networkDown() }

        clock.advance(2 * 60_000L)
        repository.refresh(force = true)
        assertEquals(MatchStatus.LIVE, repository.feed.value.matches.single().status)

        clock.advance(MatchRepositoryImpl.LIVE_REUSE_MILLIS)
        repository.refresh(force = true)
        val feed = repository.feed.value
        assertEquals(MatchStatus.STARTED, feed.matches.single().status)
        assertFalse(feed.liveStatusKnown)
        assertEquals(DataError.NETWORK, feed.error)
    }

    @Test
    fun `a 60 second poll refetches even when the fetch itself took time`() = runTest {
        api.today = {
            clock.advance(2_000) // the request takes two seconds
            json("[]")
        }
        repository.refresh()
        clock.advance(58_000) // next poll, 60s after the first one started
        repository.refresh()
        assertEquals(2, api.todayCalls)
    }

    @Test
    fun `a refresh cancelled mid-fetch doesn't leave the feed loading`() = runTest {
        api.today = { kotlinx.coroutines.awaitCancellation() }
        val job = launch { repository.refresh() }
        testScheduler.advanceUntilIdle()
        job.cancel()
        job.join()
        assertFalse(repository.feed.value.isLoading)
    }

    @Test
    fun `forced refreshes that queue behind a running one are dropped`() = runTest {
        repository.refresh()
        List(3) { async { repository.refresh(force = true) } }.awaitAll()
        assertEquals(2, api.todayCalls)
        assertEquals(2, api.liveCalls)
    }

    @Test
    fun `today failure keeps the last today payload and retries next time`() = runTest {
        api.today = { json("""[${match("a", hour)}, ${match("b", 2 * hour)}]""") }
        repository.refresh()

        clock.advance(61_000)
        api.today = { throw httpError(429) }
        api.live = { json("""[${match("b", 2 * hour)}]""") }
        repository.refresh()

        val byId = repository.feed.value.matches.associateBy { it.id }
        assertEquals(setOf("a", "b"), byId.keys)
        assertEquals(MatchStatus.LIVE, byId.getValue("b").status)

        repository.refresh()
        assertEquals(3, api.todayCalls)
    }

    @Test
    fun `total failure keeps previous matches and reports the error`() = runTest {
        api.today = { json("""[${match("a", hour)}]""") }
        repository.refresh()

        api.today = { throw httpError(429) }
        api.all = { throw httpError(429) }
        api.live = { throw httpError(429) }
        repository.refresh(force = true)

        val feed = repository.feed.value
        assertEquals(listOf("a"), feed.matches.map { it.id })
        assertEquals(DataError.RATE_LIMITED, feed.error)
        assertFalse(feed.isLoading)
    }

    @Test
    fun `maps network failures`() = runTest {
        api.today = { FakeStreamedApi.networkDown() }
        api.all = { FakeStreamedApi.networkDown() }
        api.live = { FakeStreamedApi.networkDown() }
        repository.refresh()
        assertEquals(DataError.NETWORK, repository.feed.value.error)
    }

    @Test
    fun `later days come from the all feed without duplicates`() = runTest {
        api.today = { json("""[${match("a", hour)}]""") }
        api.all = { json("""[${match("a", hour)}, ${match("tomorrow", 30 * hour)}]""") }

        repository.refresh()

        assertEquals(listOf("a", "tomorrow"), repository.feed.value.matches.map { it.id })
        assertEquals(1, api.allCalls)
    }

    @Test
    fun `an all-feed failure keeps its last payload`() = runTest {
        api.all = { json("""[${match("tomorrow", 30 * hour)}]""") }
        repository.refresh()

        api.all = { throw httpError(503) }
        api.today = { json("""[${match("a", hour)}]""") }
        repository.refresh(force = true)

        assertEquals(setOf("a", "tomorrow"), repository.feed.value.matches.map { it.id }.toSet())
        assertNull(repository.feed.value.error)
    }

    @Test
    fun `getMatch loads the feed when the match is not cached`() = runTest {
        api.today = { json("""[${match("a", hour)}]""") }
        assertEquals("a", repository.getMatch("a")?.id)
        assertNull(repository.getMatch("missing"))
        assertEquals(1, api.todayCalls)
    }
}
