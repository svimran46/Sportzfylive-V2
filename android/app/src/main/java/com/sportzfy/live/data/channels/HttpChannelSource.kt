package com.sportzfy.live.data.channels

import com.sportzfy.live.core.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Fetches the directory over HTTPS. Like the remote config, a per-minute
 * query parameter keeps GitHub's raw-file cache from hiding fresh edits.
 */
class HttpChannelSource(
    private val client: OkHttpClient,
    private val clock: Clock,
) : ChannelSource {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val separator = if ('?' in url) '&' else '?'
        val request = Request.Builder()
            .url("$url${separator}t=${clock.nowMillis() / 60_000}")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            response.body?.string() ?: throw IOException("Empty channel list")
        }
    }
}
