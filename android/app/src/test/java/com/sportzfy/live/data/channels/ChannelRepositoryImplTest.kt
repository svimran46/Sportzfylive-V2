package com.sportzfy.live.data.channels

import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.domain.model.DataError
import com.sportzfy.live.testing.FakeClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ChannelRepositoryImplTest {

    private val clock = FakeClock()
    private var url = "https://raw.example/channels.json"
    private var response: () -> String = { directory("ESPN") }
    private val fetched = mutableListOf<String>()
    private val source = object : ChannelSource {
        override suspend fun fetch(url: String): String {
            fetched += url
            yield() // a real fetch suspends, letting other refreshes queue up
            return response()
        }
    }
    private val repository = ChannelRepositoryImpl(source, NetworkJson, clock, url = { url }, ttlMillis = 60_000L)

    private fun directory(vararg names: String) =
        "const channels = [" + names.joinToString(",") { """{"name":"$it","url":"https://tv.example/$it"}""" } + "];"

    @Test
    fun `loads the directory`() = runTest {
        repository.refresh()
        val feed = repository.feed.value
        assertEquals(listOf("ESPN"), feed.channels.map { it.name })
        assertEquals(clock.now, feed.updatedAtMillis)
        assertFalse(feed.isLoading)
        assertNull(feed.error)
    }

    @Test
    fun `serves from cache within the TTL, and force or a new URL reloads`() = runTest {
        repository.refresh()
        repository.refresh()
        assertEquals(1, fetched.size)

        repository.refresh(force = true)
        assertEquals(2, fetched.size)

        url = "https://raw.example/other.json"
        repository.refresh()
        assertEquals("https://raw.example/other.json", fetched.last())
    }

    @Test
    fun `a failed load keeps the last good list and reports why`() = runTest {
        repository.refresh()

        response = { throw HttpStatusException(429) }
        repository.refresh(force = true)
        assertEquals(listOf("ESPN"), repository.feed.value.channels.map { it.name })
        assertEquals(DataError.RATE_LIMITED, repository.feed.value.error)

        response = { throw IOException("offline") }
        repository.refresh(force = true)
        assertEquals(DataError.NETWORK, repository.feed.value.error)

        response = { "<html>not a list</html>" }
        repository.refresh(force = true)
        assertEquals(DataError.UNKNOWN, repository.feed.value.error)
        assertEquals(1, repository.feed.value.channels.size)
    }

    @Test
    fun `refreshes that queue behind a running one share it`() = runTest {
        List(3) { async { repository.refresh(force = true) } }.awaitAll()
        assertEquals(1, fetched.size)
    }

    @Test
    fun `getChannel loads the directory once when needed`() = runTest {
        assertEquals("ESPN", repository.getChannel("espn")?.name)
        assertNull(repository.getChannel("missing"))
        assertEquals(1, fetched.size)
    }
}
