package com.sportzfy.live.data.config

import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.testing.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class AppConfigRepositoryImplTest {

    private val clock = FakeClock()

    private class MemoryCache(var raw: String? = null) : ConfigCache {
        override suspend fun read(): String? = raw
        override suspend fun write(raw: String) {
            this.raw = raw
        }
    }

    private var remote: () -> String = { """{"featuredMatchId":"remote"}""" }
    private var fetches = 0
    private val source = object : ConfigSource {
        override suspend fun fetch(): String {
            fetches++
            return remote()
        }
    }

    private fun repository(cache: ConfigCache = MemoryCache()) =
        AppConfigRepositoryImpl(source, cache, NetworkJson, clock, ttlMillis = 300_000L)

    @Test
    fun `fetches, applies and caches the remote config`() = runTest {
        val cache = MemoryCache()
        val repo = repository(cache)
        repo.refresh()
        assertEquals("remote", repo.config.value.featuredMatchId)
        assertEquals("""{"featuredMatchId":"remote"}""", cache.raw)
    }

    @Test
    fun `cached config applies when the network is down`() = runTest {
        remote = { throw IOException("offline") }
        val repo = repository(MemoryCache("""{"maintenance":{"enabled":true,"message":"cached"}}"""))
        repo.refresh()
        assertEquals("cached", repo.config.value.maintenanceMessage)
    }

    @Test
    fun `invalid remote json keeps the last good config`() = runTest {
        val repo = repository()
        repo.refresh()
        remote = { "<html>rate limited</html>" }
        repo.refresh(force = true)
        assertEquals("remote", repo.config.value.featuredMatchId)
    }

    @Test
    fun `refreshes are throttled by the ttl`() = runTest {
        val repo = repository()
        repo.refresh()
        clock.advance(299_000)
        repo.refresh()
        assertEquals(1, fetches)
        clock.advance(2_000)
        repo.refresh()
        assertEquals(2, fetches)
    }

    @Test
    fun `defaults until anything loads`() = runTest {
        remote = { throw IOException("offline") }
        val repo = repository()
        repo.refresh()
        assertEquals(AppConfig.Default, repo.config.value)
    }
}
