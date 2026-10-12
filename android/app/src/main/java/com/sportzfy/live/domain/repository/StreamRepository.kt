package com.sportzfy.live.domain.repository

import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.domain.model.StreamSource

interface StreamRepository {
    /**
     * Resolves every source group of a match. A group that fails (429/503,
     * timeout) contributes no streams instead of failing the whole call.
     */
    suspend fun resolveStreams(sources: List<StreamSource>): List<StreamItem>
}
