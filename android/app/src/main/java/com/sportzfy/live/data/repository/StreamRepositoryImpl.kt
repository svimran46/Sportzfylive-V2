package com.sportzfy.live.data.repository

import com.sportzfy.live.core.network.StreamedApiService
import com.sportzfy.live.core.time.Clock
import com.sportzfy.live.data.mapper.StreamMapper
import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.domain.model.StreamResolution
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.domain.repository.StreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves source groups concurrently with all-settled semantics. Each group
 * is cached for [ttlMillis]; when a refresh fails, the stale entry is served
 * instead (stale-while-error), matching the web worker's resolver.
 *
 * A group is fetched from streamed.pk directly first. If that fails (a block
 * page, an error status, a timeout or an unreadable body), [fallback] — the
 * website's Worker — is asked instead before the group counts as failed.
 */
class StreamRepositoryImpl(
    private val api: StreamedApiService,
    private val json: Json,
    private val clock: Clock,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val fallback: StreamFallback? = null,
) : StreamRepository {

    private class Entry(val streams: List<StreamItem>, val resolvedAtMillis: Long)

    private val cache = ConcurrentHashMap<StreamSource, Entry>()

    override suspend fun resolveStreams(sources: List<StreamSource>): StreamResolution = coroutineScope {
        val results = sources.distinct()
            .map { group -> async { group to resolveGroup(group) } }
            .awaitAll()
        StreamResolution(
            streams = results.flatMap { (_, result) -> result.getOrNull().orEmpty() },
            failedSources = results.filter { (_, result) -> result.isFailure }.map { it.first },
        )
    }

    private suspend fun resolveGroup(group: StreamSource): Result<List<StreamItem>> {
        val cached = cache[group]
        if (cached != null && clock.nowMillis() - cached.resolvedAtMillis < ttlMillis) {
            return Result.success(cached.streams)
        }

        val direct = try {
            val response = api.getSourceStreams(group.source, group.id)
            when {
                // The group exists upstream but has no streams yet.
                response.code() == 404 -> Result.success(emptyList())
                !response.isSuccessful -> Result.failure(HttpException(response))
                else -> Result.success(StreamMapper.toStreamItems(json, response.body(), group))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        // If the Worker fails too, the direct error is the one reported.
        val fetched = if (direct.isFailure && fallback != null) viaFallback(group).takeIf { it.isSuccess } ?: direct else direct

        fetched.onSuccess { cache[group] = Entry(it, clock.nowMillis()) }
        return if (fetched.isFailure && cached != null) Result.success(cached.streams) else fetched
    }

    private suspend fun viaFallback(group: StreamSource): Result<List<StreamItem>> = try {
        Result.success(StreamMapper.toStreamItems(json, requireNotNull(fallback).fetch(group), group))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 60_000L
    }
}
