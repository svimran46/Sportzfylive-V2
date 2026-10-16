package com.sportzfy.live

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sportzfy.live.core.player.PictureInPictureHost
import com.sportzfy.live.core.theme.SportzfyTheme
import com.sportzfy.live.presentation.config.AppConfigGate
import com.sportzfy.live.presentation.navigation.SportzfyNavHost

class MainActivity : ComponentActivity(), PictureInPictureHost {

    private var pipSourceActive = false

    override val isPictureInPictureSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportzfyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppConfigGate(versionCode = BuildConfig.VERSION_CODE) {
                        SportzfyNavHost()
                    }
                }
            }
        }
    }

    override fun setPictureInPictureSource(active: Boolean) {
        if (pipSourceActive == active) return
        pipSourceActive = active
        // Android 12+ moves the app into the window itself, animated from the gesture.
        if (isPictureInPictureSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { setPictureInPictureParams(pipParams(autoEnter = active)) }
        }
    }

    override fun enterPictureInPicture(): Boolean {
        if (!isPictureInPictureSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching { enterPictureInPictureMode(pipParams(autoEnter = pipSourceActive)) }.getOrDefault(false)
    }

    /** Android 8 to 11 have no auto-enter: Home and Recents land here first. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipSourceActive && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPictureInPicture()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnter)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
}
