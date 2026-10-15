package com.sportzfy.live.data.config

import com.sportzfy.live.core.time.Clock
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.repository.AppConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Where the raw config JSON comes from (GitHub in the app, a fake in tests). */
interface ConfigSource {
    suspend fun fetch(): String
}

/** Persists the last good raw config so it applies offline and on cold start. */
interface ConfigCache {
    suspend fun read(): String?
    suspend fun write(raw: String)
}

/**
 * Serves the cached config first, then the remote one. A fetch that fails or
 * returns invalid JSON never replaces a good config.
 */
class AppConfigRepositoryImpl(
    private val source: ConfigSource,
    private val cache: ConfigCache,
    private val json: Json,
    private val clock: Clock,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) : AppConfigRepository {

    private val state = MutableStateFlow(AppConfig.Default)
    override val config: StateFlow<AppConfig> = state.asStateFlow()

    private val mutex = Mutex()
    private var cacheLoaded = false
    private var lastFetchMillis: Long? = null

    override suspend fun refresh(force: Boolean) {
        mutex.withLock {
            if (!cacheLoaded) {
                cacheLoaded = true
                runCatching { cache.read() }.getOrNull()
                    ?.let { AppConfigParser.parse(json, it) }
                    ?.let { state.value = it }
            }

            val last = lastFetchMillis
            if (!force && last != null && clock.nowMillis() - last < ttlMillis) return

            val raw = try {
                source.fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return
            }
            val parsed = AppConfigParser.parse(json, raw) ?: return
            lastFetchMillis = clock.nowMillis()
            state.value = parsed
            runCatching { cache.write(raw) }
        }
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 5 * 60_000L
    }
}
