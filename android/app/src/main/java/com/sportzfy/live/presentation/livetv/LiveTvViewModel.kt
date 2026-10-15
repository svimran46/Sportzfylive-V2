package com.sportzfy.live.presentation.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.domain.repository.ChannelRepository
import com.sportzfy.live.presentation.model.ChannelsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Channel directory with a category filter and search. */
class LiveTvViewModel(private val channelRepository: ChannelRepository) : ViewModel() {

    private val category = MutableStateFlow<ChannelCategory?>(null)
    private val query = MutableStateFlow("")

    val state: StateFlow<ChannelsUiState> = combine(channelRepository.feed, category, query) { feed, c, q ->
        ChannelsUiState.from(feed, c, q)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChannelsUiState.from(channelRepository.feed.value, null, ""),
    )

    /** Cheap to call often: the repository holds the list for 30 minutes. */
    fun refresh(force: Boolean = false) {
        if (force && channelRepository.feed.value.isLoading) return
        viewModelScope.launch { channelRepository.refresh(force) }
    }

    fun selectCategory(value: ChannelCategory?) {
        category.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }
}
