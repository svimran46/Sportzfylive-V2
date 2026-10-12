package com.sportzfy.live.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportzfy.live.domain.model.UserSettings
import com.sportzfy.live.domain.repository.SettingsRepository
import org.koin.compose.koinInject

/** The user's Settings choices, available to every screen. */
val LocalUserSettings = compositionLocalOf { UserSettings() }

@Composable
fun ProvideUserSettings(
    repository: SettingsRepository = koinInject(),
    content: @Composable () -> Unit,
) {
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    CompositionLocalProvider(LocalUserSettings provides settings, content = content)
}
