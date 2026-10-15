package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.domain.model.ChannelFeed
import com.sportzfy.live.domain.model.DataError

data class ChannelCategoryOption(val category: ChannelCategory?, val count: Int) {
    val label: String
        get() = category?.label ?: "All"
}

data class ChannelsUiState(
    /** Nothing has loaded yet, successfully or not. */
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DataError? = null,
    val hasData: Boolean = false,
    /** "All" plus each category that has at least one channel. */
    val categories: List<ChannelCategoryOption> = emptyList(),
    val selectedCategory: ChannelCategory? = null,
    val query: String = "",
    val channels: List<Channel> = emptyList(),
) {
    /** Chips are pointless when every channel falls in one group. */
    val showCategories: Boolean
        get() = categories.size > 2

    companion object {
        fun from(feed: ChannelFeed, category: ChannelCategory?, query: String): ChannelsUiState {
            val counts = feed.channels.groupingBy { it.category }.eachCount()
            val categories = listOf(ChannelCategoryOption(null, feed.channels.size)) +
                ChannelCategory.entries.mapNotNull { c -> counts[c]?.let { ChannelCategoryOption(c, it) } }
            // A filter whose group vanished from the directory falls back to All.
            val selected = category?.takeIf { it in counts }
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val channels = feed.channels
                .filter { selected == null || it.category == selected }
                .filter { channel ->
                    val text = "${channel.name} ${channel.category.label}".lowercase()
                    words.all { it in text }
                }
            return ChannelsUiState(
                isInitialLoading = feed.updatedAtMillis == null && feed.error == null,
                isRefreshing = feed.isLoading,
                error = feed.error,
                hasData = feed.updatedAtMillis != null,
                categories = categories,
                selectedCategory = selected,
                query = query,
                channels = channels,
            )
        }
    }
}
