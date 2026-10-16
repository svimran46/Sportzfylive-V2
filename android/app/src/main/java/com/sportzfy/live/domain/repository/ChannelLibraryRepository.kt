package com.sportzfy.live.domain.repository

import com.sportzfy.live.domain.model.ChannelLibrary
import kotlinx.coroutines.flow.Flow

/** The Live TV channels the user starred or watched, persisted on the device. */
interface ChannelLibraryRepository {
    val library: Flow<ChannelLibrary>

    /** Stars [channelId] if it isn't starred yet, otherwise removes the star. */
    suspend fun toggleFavorite(channelId: String)

    /** Moves [channelId] to the front of the recently watched list. */
    suspend fun recordWatched(channelId: String)
}
