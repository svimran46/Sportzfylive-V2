package com.sportzfy.live.presentation.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportzfy.live.core.player.PlayerConfig
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.presentation.components.ChannelCard
import com.sportzfy.live.presentation.components.MessageState
import com.sportzfy.live.presentation.components.tvFocus
import com.sportzfy.live.presentation.config.LocalAppConfig
import com.sportzfy.live.presentation.watch.player.StreamPlayer
import com.sportzfy.live.presentation.watch.player.rememberStreamPlayerState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Plays one channel in the app's existing player (same WebView protections,
 * pop-up blocking and fullscreen as matches), with other channels from the
 * same group below to switch in place.
 */
@Composable
fun ChannelPlayerScreen(
    onBack: () -> Unit,
    viewModel: ChannelPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState = rememberStreamPlayerState()
    val channel = state.channel

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ChannelTopBar(channel, onBack) },
    ) { padding ->
        when {
            state.isMissing -> MessageState(
                message = "This channel is no longer available.",
                actionLabel = "Back to Live TV",
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            channel == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SportzfyColors.Rose)
            }
            else -> {
                // Only this channel's own host joins embed.st as a page the
                // player may open; every other rule stays as it is for matches.
                val base = PlayerConfig.from(LocalAppConfig.current)
                val config = base.copy(trustedHosts = base.trustedHosts + channel.sourceHost)
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StreamPlayer(
                        url = channel.streamUrl,
                        modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth().aspectRatio(16f / 9f),
                        state = playerState,
                        config = config,
                    )
                    ChannelInfo(channel, playerState.blockedCount)
                    if (state.related.isNotEmpty()) {
                        Text(
                            text = "More ${channel.category.label}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(state.related, key = { it.id }) { other ->
                                ChannelCard(other, onClick = { viewModel.selectChannel(other.id) }, modifier = Modifier.width(280.dp))
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelTopBar(channel: Channel?, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.tvFocus(CircleShape)) {
            SportzfyIcon(SportzfyIcons.Back, contentDescription = "Back")
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "LIVE TV" + (channel?.let { " · ${it.category.label.uppercase()}" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = SportzfyColors.Rose,
            )
            Text(
                text = channel?.name ?: "Loading…",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChannelInfo(channel: Channel, blockedCount: Int) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(channel.name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Source: ${channel.sourceHost}",
            style = MaterialTheme.typography.bodySmall,
            color = SportzfyColors.TextSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (blockedCount > 0) {
            Text(
                text = if (blockedCount == 1) "1 pop-up blocked" else "$blockedCount pop-ups blocked",
                style = MaterialTheme.typography.bodySmall,
                color = SportzfyColors.TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
