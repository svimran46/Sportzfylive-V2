package com.sportzfy.live.domain.repository

import kotlinx.coroutines.flow.Flow

/** Matches the user has saved, persisted on the device. */
interface FavoritesRepository {
    val favoriteIds: Flow<Set<String>>

    /** Saves [matchId] if it isn't saved yet, otherwise removes it. */
    suspend fun toggle(matchId: String)
}
