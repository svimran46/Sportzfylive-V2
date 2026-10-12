package com.sportzfy.live.presentation.watch.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sportzfy.live.core.player.PlayerUrlPolicy
import java.io.ByteArrayInputStream

/** Callbacks from the sandboxed WebView, delivered on the main thread. */
internal interface SandboxListener {
    fun onNavigationBlocked(url: String)
    fun onPageStarted()
    fun onPageFinished()
    fun onMainFrameError()
    fun onRendererGone()
    fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onExitFullscreen()
}

/**
 * Creates a WebView locked down for third-party embeds:
 * - no new windows, so `window.open` cannot create pop-ups;
 * - no file or content access, mixed content, downloads, geolocation or
 *   permission grants;
 * - top-level navigation restricted by [PlayerUrlPolicy], and ad hosts
 *   answered with an empty response.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createSandboxedWebView(
    context: Context,
    policy: PlayerUrlPolicy,
    listener: SandboxListener,
): WebView = WebView(context).apply {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setGeolocationEnabled(false)
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        // Some embeds refuse the WebView marker; present as regular Chrome.
        userAgentString = userAgentString.replace("; wv", "")
    }
    // Embedded players sit in cross-site iframes and need their own cookies
    // for anti-bot checks.
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    setBackgroundColor(Color.BLACK)
    keepScreenOn = true
    isLongClickable = false
    setOnLongClickListener { true }
    isHapticFeedbackEnabled = false
    setDownloadListener { _, _, _, _, _ -> }

    webViewClient = SandboxedWebViewClient(policy, listener)
    webChromeClient = SandboxedChromeClient(listener)
}

/**
 * Releases the renderer and media decoders. After the renderer has died the
 * WebView only accepts [WebView.destroy]; any other call can crash the app.
 */
internal fun WebView.release(rendererGone: Boolean) {
    if (!rendererGone) {
        stopLoading()
        removeAllViews()
    }
    destroy()
}

private class SandboxedWebViewClient(
    private val policy: PlayerUrlPolicy,
    private val listener: SandboxListener,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val decision = policy.navigation(url, isMainFrame = request.isForMainFrame, isRedirect = request.isRedirect)
        if (decision == PlayerUrlPolicy.Decision.BLOCK) {
            listener.onNavigationBlocked(url)
            return true
        }
        return false
    }

    // Runs on a WebView background thread; must not touch UI state.
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (policy.resource(request.url.toString()) == PlayerUrlPolicy.Decision.BLOCK) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        listener.onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        listener.onPageFinished()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) listener.onMainFrameError()
    }

    // Without this override a crashed or killed renderer takes the app down.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        listener.onRendererGone()
        return true
    }
}

private class SandboxedChromeClient(
    private val listener: SandboxListener,
) : WebChromeClient() {

    // Second line of defence behind setSupportMultipleWindows(false).
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        listener.onNavigationBlocked("window.open")
        return false
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        listener.onEnterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        listener.onExitFullscreen()
    }

    // Ad scripts use dialogs to trap the user; silently dismiss all of them.
    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult,
    ): Boolean {
        result.cancel()
        return true
    }

    override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.confirm()
        return true
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    // Replaces the grey placeholder WebView draws before a <video> starts.
    override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
}
