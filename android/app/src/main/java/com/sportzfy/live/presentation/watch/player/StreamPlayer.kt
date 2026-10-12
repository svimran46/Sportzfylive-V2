package com.sportzfy.live.presentation.watch.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.player.PlaybackSource
import com.sportzfy.live.core.player.PlayerConfig

/**
 * Dual-engine player: Media3 for `.m3u8` URLs, the sandboxed WebView for
 * everything else (the `embed.st` pages). Fills [modifier]; the caller
 * decides the aspect ratio.
 */
@Composable
fun StreamPlayer(
    url: String?,
    modifier: Modifier = Modifier,
    state: StreamPlayerState = rememberStreamPlayerState(),
    config: PlayerConfig = PlayerConfig(),
) {
    val source = remember(url) { url?.let(PlaybackSource::from) }

    LaunchedEffect(source) {
        if (source != null) state.onSourceChanged()
    }

    Box(modifier = modifier.background(Color.Black)) {
        when (source) {
            is PlaybackSource.Hls -> HlsPlayerView(source.url, state, Modifier.fillMaxSize())
            is PlaybackSource.Embed -> EmbedPlayerView(source.url, state, Modifier.fillMaxSize(), config)
            null -> PlayerMessage(
                text = if (url == null) "Pick a stream to start watching" else "This stream can't be played",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val error = state.error
        when {
            error != null -> PlayerMessage(
                text = error.message,
                modifier = Modifier.align(Alignment.Center),
                actionLabel = if (error == PlayerError.WEBVIEW_UNAVAILABLE) null else "Retry",
                onAction = state::retry,
            )
            source != null && state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val PlayerError.message: String
    get() = when (this) {
        PlayerError.LOAD_FAILED -> "Couldn't load this stream. Try another one."
        PlayerError.PLAYBACK_FAILED -> "Playback stopped. The feed may be offline."
        PlayerError.WEBVIEW_UNAVAILABLE -> "This device has no web player. Install or update Android System WebView."
    }

@Composable
private fun PlayerMessage(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
