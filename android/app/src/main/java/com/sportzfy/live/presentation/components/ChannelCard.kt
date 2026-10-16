package com.sportzfy.live.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
 * One Live TV channel tile, sized for a three-column grid: logo plate,
 * name (two lines, so every tile in a row is the same height) and sport.
 * The channel playing now is outlined and tagged LIVE; the directory
 * doesn't say whether the others are on air, so they carry no tag.
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocus(shape)
            .clip(shape)
            .background(if (isPlaying) SportzfyColors.SelectedSurface else SportzfyColors.SlateRaised)
            .border(
                if (isPlaying) BorderStroke(2.dp, SportzfyColors.Rose) else BorderStroke(1.dp, SportzfyColors.Outline),
                shape,
            )
            .clickable(role = Role.Button, onClickLabel = "Play", onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f)) {
            ChannelLogo(
                channel = channel,
                shape = RoundedCornerShape(10.dp),
                background = SportzfyColors.LogoPlate,
                modifier = Modifier.fillMaxSize(),
            )
            if (isPlaying) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(SportzfyColors.RoseFill, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingDot(size = 5.dp, color = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                        color = Color.White,
                    )
                }
            }
            if (onToggleFavorite != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .tvFocus(CircleShape)
                        .clip(CircleShape)
                        .background(SportzfyColors.Charcoal.copy(alpha = 0.55f))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            onClick = onToggleFavorite,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    SportzfyIcon(
                        SportzfyIcons.Star,
                        contentDescription = if (isFavorite) "Remove ${channel.name} from favorites" else "Add ${channel.name} to favorites",
                        tint = if (isFavorite) SportzfyColors.Gold else Color.White,
                        size = 15.dp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
            color = SportzfyColors.TextPrimary,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = channel.category.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = if (isPlaying) SportzfyColors.RoseText else SportzfyColors.TextMuted,
            maxLines = 1,
        )
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
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
