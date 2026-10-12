package com.sportzfy.live.presentation.watch.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.PlayerSurface
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

/**
 * Plays a native HLS (`.m3u8`) feed with Media3. Segments are fetched
 * through the app's OkHttp client, so they carry the same browser headers
 * as API calls.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun HlsPlayerView(
    url: String,
    state: StreamPlayerState,
    modifier: Modifier = Modifier,
    okHttpClient: OkHttpClient = koinInject(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient)))
            .build()
    }

    DisposableEffect(player) {
        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.isLoading = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY) state.error = null
            }

            override fun onPlayerError(error: PlaybackException) {
                state.isLoading = false
                state.error = PlayerError.PLAYBACK_FAILED
            }
        }
        player.addListener(playerListener)
        onDispose {
            player.removeListener(playerListener)
            player.release()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
    }

    LaunchedEffect(player, url, state.reloadToken) {
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        )
        player.prepare()
        player.playWhenReady = true
    }

    PlayerSurface(player = player, modifier = modifier)
}
