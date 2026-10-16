package com.sportzfy.live.data.channels

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sportzfy.live.data.local.FavoriteIds
import com.sportzfy.live.domain.model.ChannelLibrary
import com.sportzfy.live.domain.repository.ChannelLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class ChannelLibraryRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : ChannelLibraryRepository {

    override val library: Flow<ChannelLibrary> =
        dataStore.data
            // A disk error reads as an empty library instead of crashing the app.
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs ->
                ChannelLibrary(
                    favoriteIds = FavoriteIds.decode(prefs[FAVORITES]),
                    recentIds = FavoriteIds.decode(prefs[RECENTS]),
                )
            }
            .distinctUntilChanged()

    override suspend fun toggleFavorite(channelId: String) {
        dataStore.edit { prefs ->
            prefs[FAVORITES] = FavoriteIds.encode(FavoriteIds.toggle(FavoriteIds.decode(prefs[FAVORITES]), channelId))
        }
    }

    override suspend fun recordWatched(channelId: String) {
        dataStore.edit { prefs ->
            prefs[RECENTS] = FavoriteIds.encode(FavoriteIds.bump(FavoriteIds.decode(prefs[RECENTS]), channelId, MAX_RECENTS))
        }
    }

    private companion object {
        val FAVORITES = stringPreferencesKey("favorite_channel_ids")
        val RECENTS = stringPreferencesKey("recent_channel_ids")
        const val MAX_RECENTS = 30
    }
}
