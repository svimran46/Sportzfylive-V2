package com.sportzfy.live.presentation.watch.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a link the stream page sent outside the player, the way a browser
 * would: web links in the default browser (like a new tab), `intent:` links
 * with the app they name (or their browser fallback), `market:` links in the
 * Play Store. The stream stays loaded in the app; Back returns to it.
 */
internal fun Context.openExternally(url: String) {
    val intent = if (url.startsWith("intent:", ignoreCase = true)) {
        val parsed = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
        // Only browsable activities, never a specific component or selector.
        parsed.addCategory(Intent.CATEGORY_BROWSABLE)
        parsed.component = null
        parsed.selector = null
        val fallback = parsed.getStringExtra("browser_fallback_url")
        if (parsed.resolveActivity(packageManager) == null && fallback != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(fallback))
        } else {
            parsed
        }
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing installed can open it; stay on the stream.
    } catch (_: SecurityException) {
    }
}
