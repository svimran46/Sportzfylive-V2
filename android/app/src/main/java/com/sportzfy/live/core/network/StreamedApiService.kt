package com.sportzfy.live.core.network

import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Streamed REST API. Bodies are returned as raw [JsonElement] so one
 * malformed entry can be dropped without failing the whole payload.
 */
interface StreamedApiService {

    /** Throws `retrofit2.HttpException` on non-2xx. */
    @GET(StreamedEndpoints.MATCHES_TODAY)
    suspend fun getMatchesToday(): JsonElement

    /** Throws `retrofit2.HttpException` on non-2xx. */
    @GET(StreamedEndpoints.MATCHES_LIVE)
    suspend fun getLiveMatches(): JsonElement

    /** Returns the raw response: 404 means "no streams", not an error. */
    @GET(StreamedEndpoints.STREAM)
    suspend fun getSourceStreams(
        @Path("source") source: String,
        @Path("id") id: String,
    ): Response<JsonElement>
}
