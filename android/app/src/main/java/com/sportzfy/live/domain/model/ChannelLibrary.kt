package com.sportzfy.live.domain.model

/** Starred and recently watched channel ids, newest first. */
data class ChannelLibrary(
    val favoriteIds: List<String> = emptyList(),
    val recentIds: List<String> = emptyList(),
) {
    companion object {
        val Empty = ChannelLibrary()
    }
}
