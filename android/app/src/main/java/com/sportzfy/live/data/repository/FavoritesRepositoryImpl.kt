package com.sportzfy.live.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sportzfy.live.data.local.FavoriteIds
import com.sportzfy.live.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FavoritesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : FavoritesRepository {

    override val favoriteIds: Flow<Set<String>> =
        dataStore.data
            .map { prefs -> FavoriteIds.decode(prefs[KEY]).toSet() }
            .distinctUntilChanged()

    override suspend fun toggle(matchId: String) {
        dataStore.edit { prefs ->
            prefs[KEY] = FavoriteIds.encode(FavoriteIds.toggle(FavoriteIds.decode(prefs[KEY]), matchId))
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("favorite_match_ids")
    }
}
