package com.sportzfy.live.domain.model

/** Streams resolved for a match, plus the source groups that failed to resolve. */
data class StreamResolution(
    val streams: List<StreamItem>,
    val failedSources: List<StreamSource>,
)
