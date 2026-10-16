package com.sportzfy.live.presentation.livetv.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportzfy.live.core.player.PlaybackSource
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.presentation.components.PulsingDot
import com.sportzfy.live.presentation.components.tvFocus

/**
 * The docked player on the Live TV tab: the video's place on the page
 * ([TvDockSlot], drawn over by [TvPlayerLayer]) and the bar under it with
 * the channel, LIVE, star, mini player and full screen. Nothing while no
 * channel plays or the player is minimized.
 */
@Composable
fun TvNowPlaying(
    viewModel: TvPlayerViewModel,
    anchor: TvDockAnchor,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channel = state.channel ?: return
    if (state.presentation == TvPresentation.MINI) return

    // Capped so the grid keeps room on landscape phones and TVs.
    val maxPlayerHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SportzfyColors.SlateRaised),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No fillMaxWidth: it would force the full width and break the ratio once the cap applies.
        TvDockSlot(
            anchor = anchor,
            modifier = Modifier
                .widthIn(max = 960.dp)
                .heightIn(max = maxPlayerHeight)
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
        )
        NowPlayingBar(
            channel = channel,
            isFavorite = state.isFavorite,
            onToggleFavorite = viewModel::toggleFavorite,
            onMinimize = viewModel::minimize,
            onFullscreen = viewModel::enterFullscreen,
        )
    }
}

@Composable
private fun NowPlayingBar(
    channel: Channel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMinimize: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val isWebPage = PlaybackSource.from(channel.streamUrl, channel.streamFormat) is PlaybackSource.Embed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(size = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "  ·  LIVE",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SportzfyColors.TextSecondary,
                    maxLines = 1,
                )
            }
            channelSubtitle(channel)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = SportzfyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BarButton(
            icon = SportzfyIcons.Star,
            description = if (isFavorite) "Remove from favorites" else "Add to favorites",
            onClick = onToggleFavorite,
            tint = if (isFavorite) SportzfyColors.Gold else SportzfyColors.TextSecondary,
        )
        if (isWebPage) {
            // Escape hatch for a channel page that won't play inside the app.
            BarButton(SportzfyIcons.ExternalLink, "Open in browser", { runCatching { uriHandler.openUri(channel.streamUrl) } })
        }
        BarButton(SportzfyIcons.PictureInPicture, "Mini player", onMinimize)
        BarButton(SportzfyIcons.Fullscreen, "Full screen", onFullscreen)
    }
}

@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = SportzfyColors.TextSecondary,
) {
    IconButton(onClick = onClick, modifier = Modifier.tvFocus(CircleShape)) {
        SportzfyIcon(icon, contentDescription = description, tint = tint, size = 22.dp)
    }
}
