package com.sportzfy.live.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.Channel

/**
 * One Live TV channel tile: round logo, name, sport badge and a star. The
 * channel playing now is outlined and marked LIVE; the directory doesn't
 * say whether the others are on air, so they carry no badge.
 */
@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .tvFocus(shape)
            .clip(shape)
            .background(if (isPlaying) SportzfyColors.SelectedSurface else SportzfyColors.SlateRaised)
            .border(
                if (isPlaying) BorderStroke(2.dp, SportzfyColors.Rose) else BorderStroke(1.dp, SportzfyColors.Outline),
                shape,
            )
            .clickable(role = Role.Button, onClickLabel = "Play", onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            ChannelLogo(
                channel = channel,
                shape = CircleShape,
                background = Color.White,
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = SportzfyColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = channel.category.label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = SportzfyColors.RoseText,
                maxLines = 1,
                modifier = Modifier
                    .background(SportzfyColors.Rose.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .border(1.dp, SportzfyColors.Rose.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        if (isPlaying) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot(size = 7.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = SportzfyColors.RoseText,
                )
            }
        }
        if (onToggleFavorite != null) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).tvFocus(CircleShape),
            ) {
                SportzfyIcon(
                    SportzfyIcons.Star,
                    contentDescription = if (isFavorite) "Remove ${channel.name} from favorites" else "Add ${channel.name} to favorites",
                    tint = if (isFavorite) SportzfyColors.Gold else SportzfyColors.TextMuted,
                    size = 20.dp,
                )
            }
        }
    }
}

/** Channel logo with Coil; initials on a plain tile when it is missing or broken. */
@Composable
fun ChannelLogo(
    channel: Channel,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    background: Color = SportzfyColors.Outline,
) {
    val url = channel.logoUrl
    var failed by remember(url) { mutableStateOf(false) }
    val showImage = url != null && !failed
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (showImage) background else SportzfyColors.Outline),
        contentAlignment = Alignment.Center,
    ) {
        if (showImage) {
            AsyncImage(
                model = url,
                contentDescription = null,
                // Fit inside the circle: station logos are wide and must not be cropped.
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        } else {
            Text(
                text = initials(channel.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SportzfyColors.TextSecondary,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(' ')
        .filter { it.firstOrNull()?.isLetterOrDigit() == true }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "TV" }
