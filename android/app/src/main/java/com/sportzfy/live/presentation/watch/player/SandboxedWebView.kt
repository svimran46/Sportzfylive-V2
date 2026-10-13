package com.sportzfy.live.presentation.watch.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
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
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.sportzfy.live.core.player.ChromeUserAgent
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
 * Creates the player WebView for third-party embeds. It behaves like a
 * Chrome tab, because the stream sites are paid for by their ads and refuse
 * to play in an app's WebView:
 * - pop-up windows (the ads) open in the phone's browser, the stream stays here;
 * - navigation follows [PlayerUrlPolicy]; app links open in their app, and
 *   hosts the owner blocks remotely get an empty response;
 * - still no file or content access, downloads, geolocation or permission grants.
 *
 * [policy] is read on every request, so remote config changes apply to a
 * live player without rebuilding it.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createSandboxedWebView(
    context: Context,
    policy: () -> PlayerUrlPolicy,
    listener: SandboxListener,
): WebView = WebView(context).apply {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        // Multiple windows stay "supported" so window.open reaches
        // onCreateWindow, which opens the ad in the browser. With support
        // turned off, WebView would load the pop-up over the stream instead.
        setSupportMultipleWindows(true)
        // The sites open their ad pop-ups from script, as browsers allow.
        javaScriptCanOpenWindowsAutomatically = true
        allowFileAccess = false
        allowContentAccess = false
        // Browser default: an https stream page may still pull http media.
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        setGeolocationEnabled(false)
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        // Stream sites refuse in-app WebViews; present exactly as Chrome.
        userAgentString = ChromeUserAgent.from(userAgentString.orEmpty())
    }
    presentAsChrome(settings)
    // Embedded players sit in cross-site iframes and need their own cookies
    // for anti-bot checks.
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    setBackgroundColor(Color.BLACK)
    keepScreenOn = true
    isLongClickable = false
    setOnLongClickListener { true }
    isHapticFeedbackEnabled = false
    setDownloadListener { _, _, _, _, _ -> }
    // D-pad: the page keeps left/right/center for its own controls, but
    // up/down always leave the player so a TV remote can reach the stream
    // pills and the back button instead of getting trapped in the page.
    setOnKeyListener { view, keyCode, event ->
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            else -> return@setOnKeyListener false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
        val next = view.focusSearch(direction)
        next != null && next !== view && next.requestFocus(direction)
    }

    webViewClient = SandboxedWebViewClient(policy, listener)
    webChromeClient = SandboxedChromeClient(listener, policy)
}

/**
 * Hides the WebView signals a page can read besides the user agent string:
 * the "Android WebView" brand in `navigator.userAgentData` (and the matching
 * Sec-CH-UA headers), and the X-Requested-With header carrying the app id.
 * Each needs a recent WebView; older ones keep their defaults.
 */
private fun presentAsChrome(settings: WebSettings) {
    // Best effort: a missing or unusual WebView provider must not stop playback.
    runCatching {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val current = WebSettingsCompat.getUserAgentMetadata(settings)
            val brands = current.brandVersionList.map { brand ->
                if (brand.brand != ChromeUserAgent.WEBVIEW_BRAND) {
                    brand
                } else {
                    UserAgentMetadata.BrandVersion.Builder()
                        .setBrand(ChromeUserAgent.CHROME_BRAND)
                        .setMajorVersion(brand.majorVersion)
                        .setFullVersion(brand.fullVersion)
                        .build()
                }
            }
            WebSettingsCompat.setUserAgentMetadata(
                settings,
                UserAgentMetadata.Builder(current).setBrandVersionList(brands).build(),
            )
        }
    }
    runCatching {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
        }
    }
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
    private val policy: () -> PlayerUrlPolicy,
    private val listener: SandboxListener,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return when (policy().navigation(url, isMainFrame = request.isForMainFrame, isRedirect = request.isRedirect)) {
            PlayerUrlPolicy.Decision.ALLOW -> false
            PlayerUrlPolicy.Decision.BLOCK -> {
                listener.onNavigationBlocked(url)
                true
            }
            // App links from ads (Play Store, intent:) open in their app.
            PlayerUrlPolicy.Decision.EXTERNAL -> {
                view.context.openExternally(url)
                true
            }
        }
    }

    // Runs on a WebView background thread; must not touch UI state.
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (policy().resource(request.url.toString()) == PlayerUrlPolicy.Decision.BLOCK) {
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
    private val policy: () -> PlayerUrlPolicy,
) : WebChromeClient() {

    /**
     * Pop-ups (window.open, target=_blank) are how the stream sites show
     * their ads, and the stream only plays once the viewer has been sent
     * there. By default each one opens in the phone's browser like a new tab,
     * while the stream stays loaded here: a throwaway WebView catches the
     * pop-up's first URL and hands it over. With [PlayerUrlPolicy.dropPopups]
     * the window is refused and nothing opens.
     */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        if (policy().dropPopups) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(view.context)
        var handedOff = false
        fun handOff(target: WebView, url: String?) {
            if (handedOff || url.isNullOrBlank() || url == "about:blank") return
            handedOff = true
            view.context.openExternally(url)
            target.stopLoading()
            target.post { target.destroy() }
        }
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                handOff(v, request.url.toString())
                return true
            }

            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                handOff(v, url)
            }
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        listener.onEnterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        listener.onExitFullscreen()
    }

    // alert/confirm/prompt keep WebView's default dialogs, as in a browser:
    // some sites gate the stream behind one.

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
