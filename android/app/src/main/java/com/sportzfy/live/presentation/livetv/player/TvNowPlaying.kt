package com.sportzfy.live.presentation.livetv.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
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
            // Same 16dp gutter as the grid, so the edges line up.
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SportzfyColors.SlateRaised)
            .border(1.dp, SportzfyColors.Outline, RoundedCornerShape(20.dp)),
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
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LivePill()
                channelSubtitle(channel)?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = SportzfyColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
        BarButton(SportzfyIcons.PictureInPicture, "Mini player", onMinimize, tint = SportzfyColors.TextPrimary)
        BarButton(SportzfyIcons.Fullscreen, "Full screen", onFullscreen, tint = SportzfyColors.TextPrimary)
    }
}

/** Solid rose "● LIVE" tag. */
@Composable
internal fun LivePill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(SportzfyColors.RoseFill, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(size = 5.dp, color = androidx.compose.ui.graphics.Color.White)
        Spacer(Modifier.width(5.dp))
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = androidx.compose.ui.graphics.Color.White,
        )
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
