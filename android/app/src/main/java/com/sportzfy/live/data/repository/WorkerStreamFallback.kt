package com.sportzfy.live.data.repository

import com.sportzfy.live.domain.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Second route to a source group's streams when streamed.pk can't be reached directly. */
fun interface StreamFallback {
    /** The raw stream list for [group]; throws when this route fails too. */
    suspend fun fetch(group: StreamSource): JsonElement
}

/**
 * Asks the Sportzfy Cloudflare Worker (the one the website uses) for a
 * group's streams: `GET {baseUrl}/api/streamed/streams?source=..&id=..`.
 * streamed.pk sits behind DDoS-Guard, which can challenge requests from
 * phones; the Worker fetches from Cloudflare's edge and caches the result.
 * The response's `streams` array is read by the same mapper as the direct API.
 */
class WorkerStreamFallback(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: () -> String?,
) : StreamFallback {
    override suspend fun fetch(group: StreamSource): JsonElement = withContext(Dispatchers.IO) {
        val base = baseUrl()?.trimEnd('/') ?: throw IOException("No stream proxy configured")
        val url = "$base/api/streamed/streams".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("source", group.source)
            ?.addQueryParameter("id", group.id)
            ?.build()
            ?: throw IOException("Invalid stream proxy URL")
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Stream proxy HTTP ${response.code}")
            val body = json.parseToJsonElement(response.body?.string() ?: throw IOException("Empty proxy response"))
            // The Worker answers 200 with {"streams": [], "error": "..."} when it
            // couldn't reach upstream either; that is a failure, not "no streams".
            val obj = body as? JsonObject
            val streams = obj?.get("streams") as? JsonArray
            val error = (obj?.get("error") as? JsonPrimitive)?.content
            if (error != null && streams.isNullOrEmpty()) throw IOException("Stream proxy: $error")
            body
        }
    }
}
