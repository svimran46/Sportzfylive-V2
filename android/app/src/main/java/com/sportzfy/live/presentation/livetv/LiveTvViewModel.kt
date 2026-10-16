package com.sportzfy.live.presentation.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.domain.model.ChannelLibrary
import com.sportzfy.live.domain.repository.ChannelLibraryRepository
import com.sportzfy.live.domain.repository.ChannelRepository
import com.sportzfy.live.presentation.model.ChannelFilters
import com.sportzfy.live.presentation.model.ChannelShelf
import com.sportzfy.live.presentation.model.ChannelSort
import com.sportzfy.live.presentation.model.ChannelsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Channel directory: country, sport, shelf, sort and search. */
class LiveTvViewModel(
    private val channelRepository: ChannelRepository,
    private val library: ChannelLibraryRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(ChannelFilters())

    val state: StateFlow<ChannelsUiState> = combine(
        channelRepository.feed,
        filters,
        library.library.onStart { emit(ChannelLibrary.Empty) },
    ) { feed, f, saved ->
        ChannelsUiState.from(feed, f, saved)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChannelsUiState.from(channelRepository.feed.value),
    )

    /** Cheap to call often: the repository holds the list for 30 minutes. */
    fun refresh(force: Boolean = false) {
        if (force && channelRepository.feed.value.isLoading) return
        viewModelScope.launch { channelRepository.refresh(force) }
    }

    /** A sport chip (or All, null) leaves any shelf for the directory. */
    fun selectCategory(value: ChannelCategory?) {
        filters.update { it.copy(category = value, shelf = null) }
    }

    fun selectShelf(value: ChannelShelf) {
        filters.update { it.copy(shelf = value) }
    }

    /** null is World. Picking a country goes back to the directory. */
    fun selectCountry(value: String?) {
        filters.update { it.copy(country = value, shelf = null) }
    }

    fun setSort(value: ChannelSort) {
        filters.update { it.copy(sort = value) }
    }

    fun setQuery(value: String) {
        filters.update { it.copy(query = value) }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch { library.toggleFavorite(channelId) }
    }
}
