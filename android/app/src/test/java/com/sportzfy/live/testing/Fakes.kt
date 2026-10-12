package com.sportzfy.live.testing

import com.sportzfy.live.core.network.NetworkJson
import com.sportzfy.live.core.network.StreamedApiService
import com.sportzfy.live.core.time.Clock
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class FakeClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = now
    fun advance(millis: Long) { now += millis }
}

fun json(raw: String): JsonElement = NetworkJson.parseToJsonElement(raw)

fun httpError(code: Int): HttpException = HttpException(Response.error<Any>(code, "".toResponseBody()))

/** Scriptable [StreamedApiService]; each handler may throw to simulate failures. */
class FakeStreamedApi : StreamedApiService {
    var today: () -> JsonElement = { json("[]") }
    var live: () -> JsonElement = { json("[]") }
    var all: () -> JsonElement = { json("[]") }
    var streams: (source: String, id: String) -> Response<JsonElement> = { _, _ -> Response.success(json("[]")) }

    var todayCalls = 0
    var liveCalls = 0
    var allCalls = 0
    val streamCalls = mutableListOf<String>()

    override suspend fun getMatchesToday(): JsonElement { todayCalls++; return today() }
    override suspend fun getLiveMatches(): JsonElement { liveCalls++; return live() }
    override suspend fun getAllMatches(): JsonElement { allCalls++; return all() }
    override suspend fun getSourceStreams(source: String, id: String): Response<JsonElement> {
        streamCalls += "$source/$id"
        return streams(source, id)
    }

    companion object {
        fun networkDown(): Nothing = throw IOException("offline")

        fun errorResponse(code: Int): Response<JsonElement> = Response.error(code, "".toResponseBody())
    }
}
