package com.sportzfy.live.presentation.watch.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Shows the WebView's fullscreen video view above the whole activity:
 * landscape, system bars hidden. Undone by [exit] (user back press) or
 * [teardown] (the page left fullscreen itself).
 */
internal class FullscreenHost(
    private val activity: Activity,
    private val onChanged: (Boolean) -> Unit,
) {
    private var customView: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private val decor: ViewGroup?
        get() = activity.window.decorView as? ViewGroup

    fun enter(view: View, callback: WebChromeClient.CustomViewCallback) {
        val container = decor
        if (customView != null || container == null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        this.callback = callback

        view.setBackgroundColor(Color.BLACK)
        view.keepScreenOn = true
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.getInsetsController(activity.window, container).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onChanged(true)
    }

    fun teardown() {
        val view = customView ?: return
        val container = decor
        container?.removeView(view)
        customView = null
        callback = null

        activity.requestedOrientation = previousOrientation
        if (container != null) {
            WindowCompat.getInsetsController(activity.window, container).show(WindowInsetsCompat.Type.systemBars())
        }
        onChanged(false)
    }

    fun exit() {
        val pending = callback
        teardown()
        pending?.onCustomViewHidden()
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
