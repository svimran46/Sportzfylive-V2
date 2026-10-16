package com.sportzfy.live.presentation.watch.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class PlayerError {
    /** The embed page failed to load (network, DNS, TLS). */
    LOAD_FAILED,

    /** Media3 could not play the stream (network, server, broken feed). */
    PLAYBACK_FAILED,

    /** The device can't decode the stream, or Media3 can't read its container. Retrying won't help. */
    UNSUPPORTED_FORMAT,

    /** The stream is DRM-encrypted; the app has no licence to play it. Retrying won't help. */
    DRM_PROTECTED,

    /** No WebView provider is installed (seen on some TV devices). */
    WEBVIEW_UNAVAILABLE,
}

/** Pauses and resumes the current Media3 stream. */
internal fun interface PlaybackTransport {
    fun setPaused(paused: Boolean)
}

/** Observable player status, hoisted so the watch screen can react to it. */
@Stable
class StreamPlayerState {
    var isLoading by mutableStateOf(false)
        internal set

    var error by mutableStateOf<PlayerError?>(null)
        internal set

    /** Pop-ups and redirects blocked since the current stream started. */
    var blockedCount by mutableIntStateOf(0)
        internal set

    var isFullscreen by mutableStateOf(false)
        internal set

    /** Paused by the user. Only Media3 streams can pause; see [canPause]. */
    var isPaused by mutableStateOf(false)
        internal set

    /** Applied to Media3 streams; a web page keeps its own volume. */
    var isMuted by mutableStateOf(false)
        private set

    /** Set while a Media3 stream is on screen; web pages have their own controls. */
    internal var transport by mutableStateOf<PlaybackTransport?>(null)

    val canPause: Boolean
        get() = transport != null

    fun togglePause() {
        transport?.setPaused(!isPaused)
    }

    fun toggleMute() {
        isMuted = !isMuted
    }

    /** Bumped to force the current stream to reload. */
    internal var reloadToken by mutableIntStateOf(0)
        private set

    fun retry() {
        error = null
        reloadToken++
    }

    internal fun onSourceChanged() {
        isLoading = true
        isPaused = false
        error = null
        blockedCount = 0
    }
}

@Composable
fun rememberStreamPlayerState(): StreamPlayerState = remember { StreamPlayerState() }
