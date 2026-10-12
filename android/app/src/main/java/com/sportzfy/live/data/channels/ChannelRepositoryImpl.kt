package com.sportzfy.live.data.channels

import com.sportzfy.live.core.network.httpStatusToDataError
import com.sportzfy.live.core.network.toDataError
import com.sportzfy.live.core.time.Clock
import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelFeed
import com.sportzfy.live.domain.model.DataError
import com.sportzfy.live.domain.repository.ChannelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException

/** Where the raw directory comes from (GitHub in the app, a fake in tests). */
interface ChannelSource {
    suspend fun fetch(url: String): String
}

/** A non-2xx answer, kept apart from connectivity errors so the message fits. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * Loads the channel directory from [url] (read on every refresh, so a new
 * `channelsUrl` in the remote config applies on the next load). The list is
 * held for [ttlMillis]; a failed load keeps the last good list and reports
 * [ChannelFeed.error]. Concurrent refreshes share one request.
 */
class ChannelRepositoryImpl(
    private val source: ChannelSource,
    private val json: Json,
    private val clock: Clock,
    private val url: () -> String,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) : ChannelRepository {

    private val state = MutableStateFlow(ChannelFeed.Initial)
    override val feed: StateFlow<ChannelFeed> = state.asStateFlow()

    private val mutex = Mutex()
    private var lastSuccessMillis: Long? = null
    private var lastUrl: String? = null

    @Volatile
    private var fetchCount = 0L

    override suspend fun refresh(force: Boolean) {
        val seen = fetchCount
        mutex.withLock {
            // A load finished while this call waited for the lock; it is fresh enough.
            if (fetchCount != seen) return
            val target = url()
            val last = lastSuccessMillis
            if (!force && target == lastUrl && last != null && clock.nowMillis() - last < ttlMillis) return

            state.update { it.copy(isLoading = true) }
            val channels = try {
                ChannelParser.parse(json, source.fetch(target))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fetchCount++
                val error = if (e is HttpStatusException) httpStatusToDataError(e.code) else e.toDataError()
                state.update { it.copy(isLoading = false, error = error) }
                return
            }
            fetchCount++
            if (channels == null) {
                state.update { it.copy(isLoading = false, error = DataError.UNKNOWN) }
                return
            }
            val now = clock.nowMillis()
            lastSuccessMillis = now
            lastUrl = target
            state.value = ChannelFeed(channels, updatedAtMillis = now, isLoading = false, error = null)
        }
    }

    override suspend fun getChannel(id: String): Channel? {
        state.value.channels.firstOrNull { it.id == id }?.let { return it }
        refresh()
        return state.value.channels.firstOrNull { it.id == id }
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 30 * 60_000L
    }
}
