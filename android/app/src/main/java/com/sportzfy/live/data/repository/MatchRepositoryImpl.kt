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
 * Fetches `all-today`, `all` (later days) and `live` concurrently and merges
 * them into one de-duplicated list. Results are held in memory for
 * [ttlMillis] so rapid tab switches never reach upstream; concurrent
 * refreshes share one request through [mutex].
 *
 * Failure isolation:
 * - `live` fails → its last good payload is reused for up to
 *   [LIVE_REUSE_MILLIS]; after that nothing is marked live, matches that
 *   have kicked off are STARTED, and [MatchFeed.liveStatusKnown] is false.
 * - `all-today` or `all` fails → its last good payload is merged with the
 *   fresh feeds; a failed `all-today` keeps the cache stale so the next call
 *   retries.
 * - all three fail → the previous matches stay and [MatchFeed.error] is set.
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
    private var lastAllPayload: List<RemoteMatchDto>? = null
    private var lastLivePayload: List<RemoteMatchDto>? = null
    private var lastLiveMillis: Long? = null
    private var lastSuccessMillis: Long? = null

    /** Counts finished fetches, so a refresh that queued behind one can skip. */
    @Volatile
    private var fetchCount = 0L

    override suspend fun refresh(force: Boolean) {
        val seen = fetchCount
        mutex.withLock {
            // A fetch finished while this call waited: its data is as fresh as
            // ours would be, so taps on Refresh never stack up requests.
            if (fetchCount != seen) return
            val last = lastSuccessMillis
            if (!force && last != null && clock.nowMillis() - last < ttlMillis) return

            state.update { it.copy(isLoading = true) }
            val (today, all, live) = coroutineScope {
                val today = async { fetchList(api::getMatchesToday) }
                val all = async { fetchList(api::getAllMatches) }
                val live = async { fetchList(api::getLiveMatches) }
                Triple(today.await(), all.await(), live.await())
            }
            fetchCount++

            val now = clock.nowMillis()
            val todayList = today.getOrNull()
            val allList = all.getOrNull()
            val liveList = live.getOrNull()
            if (todayList == null && allList == null && liveList == null) {
                state.update { it.copy(isLoading = false, error = today.exceptionOrNull().toDataError()) }
                return
            }

            if (todayList != null) {
                lastTodayPayload = todayList
                lastSuccessMillis = now
            }
            if (allList != null) lastAllPayload = allList
            if (liveList != null) {
                lastLivePayload = liveList
                lastLiveMillis = now
            }
            val liveFresh = lastLiveMillis?.let { now - it <= LIVE_REUSE_MILLIS } == true
            val liveForMerge = liveList ?: lastLivePayload?.takeIf { liveFresh }
            // all-today first so its entries win when both feeds list a match.
            val base = (todayList ?: lastTodayPayload.orEmpty()) + (allList ?: lastAllPayload.orEmpty())
            state.value = MatchFeed(
                matches = MatchMapper.merge(base, liveForMerge, now),
                updatedAtMillis = now,
                isLoading = false,
                error = null,
                liveStatusKnown = liveForMerge != null,
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

        /** How long a good `live` payload stands in for a failed one. */
        const val LIVE_REUSE_MILLIS = 5 * 60_000L
    }
}
