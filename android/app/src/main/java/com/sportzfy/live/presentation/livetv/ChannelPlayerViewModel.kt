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
    /** Other channels in the same group, for switching without leaving the player. */
    val related: List<Channel> = emptyList(),
)

class ChannelPlayerViewModel(
    initialChannelId: String,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelPlayerUiState())
    val state: StateFlow<ChannelPlayerUiState> = _state.asStateFlow()

    init {
        selectChannel(initialChannelId)
    }

    fun selectChannel(id: String) {
        if (_state.value.channel?.id == id) return
        viewModelScope.launch {
            val channel = channelRepository.getChannel(id)
            _state.value = if (channel == null) {
                ChannelPlayerUiState(isLoading = false, isMissing = true)
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
