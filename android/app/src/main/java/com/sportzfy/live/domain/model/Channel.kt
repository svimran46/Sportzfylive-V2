package com.sportzfy.live.domain.model

/** A 24/7 TV channel from the channel directory (`channels.json`). */
data class Channel(
    /** Slug of the name, unique within the directory; used as the route argument. */
    val id: String,
    val name: String,
    val logoUrl: String?,
    val category: ChannelCategory,
    /** https page or `.m3u8` stream, played by the existing player. */
    val streamUrl: String,
) {
    /** Host serving the stream, shown as the channel's source. */
    val sourceHost: String
        get() = streamUrl.substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase()
}

/** Live TV filter groups. Every channel belongs to exactly one. */
enum class ChannelCategory(val label: String) {
    FOOTBALL("Football"),
    CRICKET("Cricket"),
    OTHERS("Others"),
}

/** Snapshot of the channel directory; keeps the last good list when a refresh fails. */
data class ChannelFeed(
    val channels: List<Channel>,
    val updatedAtMillis: Long?,
    val isLoading: Boolean,
    val error: DataError?,
) {
    companion object {
        val Initial = ChannelFeed(channels = emptyList(), updatedAtMillis = null, isLoading = false, error = null)
    }
}
