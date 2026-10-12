package com.sportzfy.live.domain.model

/** A single playable feed inside a [StreamSource] group. */
data class StreamItem(
    val source: String,
    val sourceId: String,
    val streamNo: Int,
    val language: String,
    val isHd: Boolean,
) {
    val embedUrl: String
        get() = EmbedUrl.build(source, sourceId, streamNo)

    /** Stable key across refreshes, used for selection state and list keys. */
    val key: String
        get() = "$source/$sourceId/$streamNo"
}
