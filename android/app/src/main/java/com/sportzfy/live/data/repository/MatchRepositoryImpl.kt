package com.sportzfy.live.data.repository

import com.sportzfy.live.core.network.StreamedApiService
import com.sportzfy.live.core.network.toDataError
import com.sportzfy.live.core.time.Clock
import com.sportzfy.live.data.mapper.MatchMapper
import com.sportzfy.live.data.model.RemoteMatchDto
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.repository.MatchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Fetches `all-today` and `live` concurrently and merges them. Results are
 * held in memory for [ttlMillis] so rapid tab switches never reach upstream;
 * concurrent refreshes share one request through [mutex].
 *
 * Failure isolation:
 * - `live` fails → statuses fall back to kick-off time.
 * - `all-today` fails → the last good `all-today` payload is merged with the
 *   fresh `live` feed, and the cache stays stale so the next call retries.
 * - both fail → the previous matches stay and [MatchFeed.error] is set.
 */
class MatchRepositoryImpl(
    private val api: StreamedApiService,
    private val json: Json,
    private val clock: Clock,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) : MatchRepository {

    private val state = MutableStateFlow(MatchFeed.Initial)
    override val feed: StateFlow<MatchFeed> = state.asStateFlow()

    private val mutex = Mutex()
    private var lastTodayPayload: List<RemoteMatchDto>? = null
    private var lastSuccessMillis: Long? = null

    override suspend fun refresh(force: Boolean) {
        mutex.withLock {
            val last = lastSuccessMillis
            if (!force && last != null && clock.nowMillis() - last < ttlMillis) return

            state.update { it.copy(isLoading = true) }
            val (today, live) = coroutineScope {
                val today = async { fetchList(api::getMatchesToday) }
                val live = async { fetchList(api::getLiveMatches) }
                today.await() to live.await()
            }

            val now = clock.nowMillis()
            val todayList = today.getOrNull()
            val liveList = live.getOrNull()
            if (todayList == null && liveList == null) {
                state.update { it.copy(isLoading = false, error = today.exceptionOrNull().toDataError()) }
                return
            }

            if (todayList != null) {
                lastTodayPayload = todayList
                lastSuccessMillis = now
            }
            state.value = MatchFeed(
                matches = MatchMapper.merge(todayList ?: lastTodayPayload.orEmpty(), liveList, now),
                updatedAtMillis = now,
                isLoading = false,
                error = null,
            )
        }
    }

    override suspend fun getMatch(id: String): Match? {
        state.value.matches.firstOrNull { it.id == id }?.let { return it }
        refresh()
        return state.value.matches.firstOrNull { it.id == id }
    }

    private suspend fun fetchList(call: suspend () -> JsonElement): Result<List<RemoteMatchDto>> =
        try {
            Result.success(MatchMapper.decodeList(json, call()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        const val DEFAULT_TTL_MILLIS = 60_000L
    }
}
