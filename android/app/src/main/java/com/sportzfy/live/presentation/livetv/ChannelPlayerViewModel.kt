package com.sportzfy.live.presentation.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelPlayerUiState(
    val channel: Channel? = null,
    val isLoading: Boolean = true,
    /** The id isn't in the directory (removed upstream, or a stale back stack). */
    val isMissing: Boolean = false,
    /** The directory failed to load, so the channel couldn't be looked up. */
    val isLoadFailed: Boolean = false,
    /** Other channels in the same group, for switching without leaving the player. */
    val related: List<Channel> = emptyList(),
)

class ChannelPlayerViewModel(
    initialChannelId: String,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelPlayerUiState())
    val state: StateFlow<ChannelPlayerUiState> = _state.asStateFlow()

    private var requestedId = initialChannelId

    init {
        selectChannel(initialChannelId)
    }

    /** Tries the last requested channel again after a failed load. */
    fun retry() {
        _state.value = ChannelPlayerUiState()
        selectChannel(requestedId)
    }

    fun selectChannel(id: String) {
        if (_state.value.channel?.id == id) return
        requestedId = id
        viewModelScope.launch {
            val channel = channelRepository.getChannel(id)
            _state.value = if (channel == null) {
                ChannelPlayerUiState(
                    isLoading = false,
                    isMissing = true,
                    isLoadFailed = channelRepository.feed.value.error != null,
                )
            } else {
                ChannelPlayerUiState(
                    channel = channel,
                    isLoading = false,
                    related = channelRepository.feed.value.channels
                        .filter { it.category == channel.category && it.id != channel.id }
                        .take(MAX_RELATED),
                )
            }
        }
    }

    private companion object {
        const val MAX_RELATED = 20
    }
}
