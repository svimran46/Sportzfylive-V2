package com.sportzfy.live.presentation.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.ClockFormat
import com.sportzfy.live.domain.model.UserSettings
import com.sportzfy.live.presentation.components.SegmentedControl
import com.sportzfy.live.presentation.components.tvFocus
import org.koin.compose.viewmodel.koinViewModel

private const val ANY_LANGUAGE = "Any language"
private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    versionName: String,
    versionCode: Int,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        versionName = versionName,
        versionCode = versionCode,
        onBack = onBack,
        onSetLanguage = viewModel::setPreferredLanguage,
        onSetClockFormat = viewModel::setClockFormat,
        onClearSaved = viewModel::clearSavedMatches,
    )
}

/** Stateless Settings UI; dialogs keep only their open/closed flag. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    versionName: String,
    versionCode: Int,
    onBack: () -> Unit,
    onSetLanguage: (String?) -> Unit,
    onSetClockFormat: (ClockFormat) -> Unit,
    onClearSaved: () -> Unit,
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val language = state.settings.preferredLanguage
    val savedCount = state.savedCount

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(title = "Settings", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel("Playback")
                SettingsCard {
                    SettingsRow(
                        icon = SportzfyIcons.Language,
                        title = "Preferred stream language",
                        subtitle = language ?: ANY_LANGUAGE,
                        onClick = { showLanguageDialog = true },
                    )
                    Text(
                        text = "The player starts on a stream in this language when one is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SportzfyColors.TextMuted,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )
                }

                SectionLabel("Display")
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SportzfyIcon(SportzfyIcons.Clock, contentDescription = null, tint = SportzfyColors.TextSecondary)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "Time format",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SportzfyColors.TextPrimary,
                        )
                    }
                    SegmentedControl(
                        options = ClockFormat.entries.map { it.label },
                        selectedIndex = state.settings.clockFormat.ordinal,
                        onSelect = { index -> onSetClockFormat(ClockFormat.entries[index]) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }

                SectionLabel("Saved matches")
                SettingsCard {
                    SettingsRow(
                        icon = SportzfyIcons.Delete,
                        title = "Clear saved matches",
                        subtitle = if (savedCount == 0) "No saved matches" else "$savedCount saved",
                        onClick = if (savedCount > 0) ({ showClearDialog = true }) else null,
                        showChevron = false,
                    )
                }

                SectionLabel("App")
                SettingsCard {
                    SettingsRow(
                        icon = SportzfyIcons.About,
                        title = "Version",
                        value = "$versionName ($versionCode)",
                        onClick = null,
                        showChevron = false,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selected = language,
            onSelect = {
                onSetLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showClearDialog && savedCount > 0) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = SportzfyColors.SlateRaised,
            titleContentColor = SportzfyColors.TextPrimary,
            textContentColor = SportzfyColors.TextSecondary,
            title = { Text("Clear saved matches?") },
            text = {
                Text(
                    if (savedCount == 1) {
                        "This removes 1 saved match from this device."
                    } else {
                        "This removes all $savedCount saved matches from this device."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSaved()
                        showClearDialog = false
                    },
                    modifier = Modifier.tvFocus(RoundedCornerShape(20.dp)),
                ) {
                    Text("Clear", color = SportzfyColors.RoseText, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.tvFocus(RoundedCornerShape(20.dp)),
                ) {
                    Text("Cancel", color = SportzfyColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun LanguageDialog(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options: List<String?> = listOf<String?>(null) + UserSettings.LANGUAGES
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SportzfyColors.SlateRaised,
        titleContentColor = SportzfyColors.TextPrimary,
        textContentColor = SportzfyColors.TextPrimary,
        title = { Text("Stream language") },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    val shape = RoundedCornerShape(10.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .tvFocus(shape, focusedScale = 1f)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = SportzfyColors.Rose,
                                unselectedColor = SportzfyColors.TextMuted,
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = option ?: ANY_LANGUAGE,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(20.dp))) {
                Text("Cancel", color = SportzfyColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.tvFocus(CircleShape)) {
            SportzfyIcon(SportzfyIcons.Back, contentDescription = "Back")
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .testTag("screen-title")
                .semantics { heading() },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
        color = SportzfyColors.RoseText,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SportzfyColors.SlateRaised, CardShape),
    ) {
        content()
    }
}

/**
 * One settings line. With [onClick] null the row is plain text: not
 * focusable, not clickable, and drawn muted when [subtitle] says it is empty.
 */
@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: (() -> Unit)?,
    value: String? = null,
    subtitle: String? = null,
    showChevron: Boolean = true,
) {
    val enabled = onClick != null
    val interactive = if (onClick != null) {
        Modifier
            .tvFocus(CardShape, focusedScale = 1.01f)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    // A disabled action row (one with a subtitle and no click) reads muted;
    // informational rows such as Version keep normal colours.
    val muted = !enabled && subtitle != null
    val titleColor = if (muted) SportzfyColors.TextMuted else SportzfyColors.TextPrimary
    val iconTint = if (muted) SportzfyColors.TextMuted else SportzfyColors.TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(interactive)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportzfyIcon(icon, contentDescription = null, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (muted) SportzfyColors.TextMuted else SportzfyColors.TextSecondary,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = SportzfyColors.TextSecondary,
                maxLines = 1,
            )
        }
        if (showChevron && enabled) {
            Spacer(Modifier.width(6.dp))
            SportzfyIcon(
                SportzfyIcons.ChevronRight,
                contentDescription = null,
                tint = SportzfyColors.TextMuted,
                size = 18.dp,
            )
        }
    }
}
