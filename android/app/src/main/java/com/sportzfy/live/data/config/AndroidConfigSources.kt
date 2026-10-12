package com.sportzfy.live.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sportzfy.live.core.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Fetches the config from GitHub. raw.githubusercontent.com caches files for
 * about five minutes; a per-minute query parameter keeps edits from waiting
 * out that cache.
 */
class HttpConfigSource(
    private val client: OkHttpClient,
    private val url: String,
    private val clock: Clock,
) : ConfigSource {
    override suspend fun fetch(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$url?t=${clock.nowMillis() / 60_000}")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Config HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty config")
        }
    }
}

class DataStoreConfigCache(private val dataStore: DataStore<Preferences>) : ConfigCache {
    override suspend fun read(): String? = dataStore.data.first()[KEY]

    override suspend fun write(raw: String) {
        dataStore.edit { it[KEY] = raw }
    }

    private companion object {
        val KEY = stringPreferencesKey("remote_config_json")
    }
}
