package com.sportzfy.live.presentation.watch.player

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sportzfy.live.core.network.StreamedEndpoints
import com.sportzfy.live.core.player.EmbedFrame
import com.sportzfy.live.core.player.PlayerConfig
import com.sportzfy.live.core.player.PlayerUrlPolicy
import com.sportzfy.live.domain.model.EmbedUrl
import com.sportzfy.live.domain.model.PlayerMode

/**
 * Plays an `embed.st` page. In [PlayerMode.IFRAME] (the default) the embed
 * runs in a plain iframe inside a wrapper page, exactly like the website; in
 * [PlayerMode.DIRECT] it is the WebView's page itself.
 *
 * The WebView is created once and reused: a new [url], mode or
 * [StreamPlayerState.retry] reloads the same instance, so switching streams
 * never rebuilds the surrounding layout. A fresh WebView is created only
 * after its renderer process dies.
 */
@Composable
internal fun EmbedPlayerView(
    url: String,
    state: StreamPlayerState,
    modifier: Modifier = Modifier,
    config: PlayerConfig = PlayerConfig(),
) {
    val policy = remember(config.blockedHosts, config.trustedHosts) {
        PlayerUrlPolicy(trustedHosts = setOf(EmbedUrl.HOST) + config.trustedHosts, blockedHosts = config.blockedHosts)
    }
    val currentPolicy by rememberUpdatedState(policy)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var generation by remember { mutableIntStateOf(0) }
    val holder = remember { WebViewHolder() }

    val fullscreen = remember(context) {
        context.findActivity()?.let { activity -> FullscreenHost(activity) { state.isFullscreen = it } }
    }

    val listener = remember(state, fullscreen) {
        object : SandboxListener {
            override fun onNavigationBlocked(url: String) {
                state.blockedCount++
            }

            override fun onPageStarted() {
                state.isLoading = true
            }

            override fun onPageFinished() {
                state.isLoading = false
            }

            override fun onMainFrameError() {
                state.isLoading = false
                state.error = PlayerError.LOAD_FAILED
            }

            override fun onRendererGone() {
                // The dead WebView is released by onRelease once the new one replaces it.
                fullscreen?.teardown()
                holder.deadWebView = holder.webView
                holder.webView = null
                generation++
            }

            override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (fullscreen != null) fullscreen.enter(view, callback) else callback.onCustomViewHidden()
            }

            override fun onExitFullscreen() {
                fullscreen?.teardown()
            }
        }
    }

    BackHandler(enabled = state.isFullscreen) { fullscreen?.exit() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> holder.webView?.onPause()
                Lifecycle.Event.ON_RESUME -> holder.webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // teardown, not exit: the WebView may already be destroyed.
            fullscreen?.teardown()
        }
    }

    key(generation) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                try {
                    createSandboxedWebView(ctx, { currentPolicy }, listener).also { holder.webView = it }
                } catch (e: Exception) {
                    // Thrown when the device has no (or a broken) WebView provider.
                    state.isLoading = false
                    state.error = PlayerError.WEBVIEW_UNAVAILABLE
                    FrameLayout(ctx)
                }
            },
            update = { view ->
                val webView = view as? WebView ?: return@AndroidView
                val request = LoadRequest(url, state.reloadToken, config.mode)
                if (webView.tag != request) {
                    webView.tag = request
                    when (config.mode) {
                        PlayerMode.IFRAME -> webView.loadDataWithBaseURL(
                            EmbedFrame.BASE_URL,
                            EmbedFrame.html(url),
                            "text/html",
                            "utf-8",
                            null,
                        )
                        PlayerMode.DIRECT -> webView.loadUrl(url, mapOf("Referer" to StreamedEndpoints.REFERER))
                    }
                }
            },
            onRelease = { view ->
                if (view is WebView) {
                    val rendererGone = holder.deadWebView === view
                    if (rendererGone) holder.deadWebView = null
                    if (holder.webView === view) holder.webView = null
                    view.release(rendererGone)
                }
            },
        )
    }
}

private class WebViewHolder {
    var webView: WebView? = null
    var deadWebView: WebView? = null
}

private data class LoadRequest(val url: String, val reloadToken: Int, val mode: PlayerMode)
