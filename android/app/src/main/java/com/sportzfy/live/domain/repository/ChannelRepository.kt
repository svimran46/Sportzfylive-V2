package com.sportzfy.live.domain.repository

import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelFeed
import kotlinx.coroutines.flow.StateFlow

interface ChannelRepository {
    val feed: StateFlow<ChannelFeed>

    /** Reloads the directory unless the last good copy is still fresh, or [force] is set. */
    suspend fun refresh(force: Boolean = false)

    /** Looks [id] up in the loaded directory, loading it once if needed. */
    suspend fun getChannel(id: String): Channel?
}
