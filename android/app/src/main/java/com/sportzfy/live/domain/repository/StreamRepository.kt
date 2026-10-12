package com.sportzfy.live.domain.repository

import com.sportzfy.live.domain.model.StreamResolution
import com.sportzfy.live.domain.model.StreamSource

interface StreamRepository {
    /**
     * Resolves every source group of a match concurrently. A group that fails
     * (429/503, timeout) is reported in [StreamResolution.failedSources]
     * instead of failing the whole call.
     */
    suspend fun resolveStreams(sources: List<StreamSource>): StreamResolution
}
