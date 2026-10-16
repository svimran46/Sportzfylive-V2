package com.sportzfy.live.presentation.livetv.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelLibrary
import com.sportzfy.live.domain.repository.ChannelLibraryRepository
import com.sportzfy.live.domain.repository.ChannelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the one TV player is drawn. */
enum class TvPresentation {
    /** Above the channel grid on the Live TV tab. */
    DOCKED,

    /** A floating window over the whole app, draggable, while the user browses. */
    MINI,

    /** The whole screen, landscape, system bars hidden. */
    FULLSCREEN,
}

data class TvPlayerUiState(
    val channel: Channel? = null,
    /** Channel ids previous/next step through: the grid the channel was picked from. */
    val queue: List<String> = emptyList(),
    val presentation: TvPresentation = TvPresentation.DOCKED,
    val isFavorite: Boolean = false,
) {
    /** Zapping needs somewhere to go. */
    val canZap: Boolean
        get() = queue.size > 1
}

/**
 * The Live TV player session, scoped to the activity so a channel keeps
 * playing (docked, in the mini player or full screen) while the user moves
 * between tabs. One channel plays at a time.
 */
class TvPlayerViewModel(
    private val channelRepository: ChannelRepository,
    private val library: ChannelLibraryRepository,
) : ViewModel() {

    private val session = MutableStateFlow(TvPlayerUiState())
    private var loadJob: Job? = null

    val state: StateFlow<TvPlayerUiState> = combine(
        session,
        library.library.onStart { emit(ChannelLibrary.Empty) },
    ) { s, saved ->
        s.copy(isFavorite = s.channel != null && s.channel.id in saved.favoriteIds)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TvPlayerUiState())

    /**
     * Plays [channelId]; [queue] (the grid it was picked from) is what
     * previous/next walk. Picking the channel already playing just keeps it.
     */
    fun play(channelId: String, queue: List<String> = emptyList()) {
        val zapQueue = queue.ifEmpty { session.value.queue }.let { if (channelId in it) it else listOf(channelId) }
        if (session.value.channel?.id == channelId) {
            session.update { it.copy(queue = zapQueue) }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val channel = channelRepository.getChannel(channelId) ?: return@launch
            session.update { current ->
                current.copy(
                    channel = channel,
                    queue = zapQueue,
                    // A fresh session opens docked; zapping keeps the mini or full screen player.
                    presentation = if (current.channel == null) TvPresentation.DOCKED else current.presentation,
                )
            }
            library.recordWatched(channel.id)
        }
    }

    fun next() = zap(+1)

    fun previous() = zap(-1)

    /** Steps through the queue, wrapping at both ends like a TV remote. */
    private fun zap(step: Int) {
        val current = session.value
        val id = current.channel?.id ?: return
        if (!current.canZap) return
        val index = current.queue.indexOf(id).takeIf { it >= 0 } ?: return
        val size = current.queue.size
        play(current.queue[((index + step) % size + size) % size])
    }

    fun minimize() = present(TvPresentation.MINI)

    fun expand() = present(TvPresentation.DOCKED)

    fun enterFullscreen() = present(TvPresentation.FULLSCREEN)

    fun exitFullscreen() = present(TvPresentation.DOCKED)

    private fun present(value: TvPresentation) {
        if (session.value.channel != null) session.update { it.copy(presentation = value) }
    }

    /** Stops playback and removes the player (mini player ×, or a match starting). */
    fun close() {
        loadJob?.cancel()
        session.value = TvPlayerUiState()
    }

    fun toggleFavorite() {
        val id = session.value.channel?.id ?: return
        viewModelScope.launch { library.toggleFavorite(id) }
    }
}
