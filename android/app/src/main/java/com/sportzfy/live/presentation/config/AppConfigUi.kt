package com.sportzfy.live.presentation.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.domain.repository.AppConfigRepository
import com.sportzfy.live.presentation.components.tvFocus
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** The remote config, available to every screen. */
val LocalAppConfig = compositionLocalOf { AppConfig.Default }

private const val CONFIG_REFRESH_MILLIS = 5 * 60_000L

/**
 * Loads the remote config, keeps it fresh while the app is visible, and
 * blocks the app behind a maintenance or update screen when it says so.
 */
@Composable
fun AppConfigGate(
    versionCode: Int,
    repository: AppConfigRepository = koinInject(),
    content: @Composable () -> Unit,
) {
    val config by repository.config.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(repository, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                repository.refresh()
                delay(CONFIG_REFRESH_MILLIS)
            }
        }
    }

    CompositionLocalProvider(LocalAppConfig provides config) {
        val maintenance = config.maintenanceMessage
        when {
            maintenance != null -> GateScreen(title = "Under maintenance", message = maintenance)
            config.requiresUpdate(versionCode) -> GateScreen(
                title = "Update required",
                message = "This version of Sportzfy is no longer supported. Install the latest version to keep watching.",
                actionLabel = config.updateUrl?.let { "Get the update" },
                actionUrl = config.updateUrl,
            )
            else -> content()
        }
    }
}

@Composable
private fun GateScreen(
    title: String,
    message: String,
    actionLabel: String? = null,
    actionUrl: String? = null,
) {
    val uriHandler = LocalUriHandler.current
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = SportzfyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && actionUrl != null) {
                Button(
                    onClick = { runCatching { uriHandler.openUri(actionUrl) } },
                    colors = ButtonDefaults.buttonColors(containerColor = SportzfyColors.RoseFill),
                    modifier = Modifier.tvFocus(RoundedCornerShape(50)),
                ) { Text(actionLabel) }
            }
        }
    }
}
