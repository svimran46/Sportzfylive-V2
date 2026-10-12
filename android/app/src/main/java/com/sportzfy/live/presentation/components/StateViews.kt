package com.sportzfy.live.presentation.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.Announcement

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SportzfyColors.Rose)
    }
}

/** Placeholder cards that gently pulse while the first load runs. */
@Composable
fun MatchListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
    contentDescription: String = "Loading matches",
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .semantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .height(28.dp)
                .graphicsLayer { alpha = pulse }
                .background(SportzfyColors.SlateRaised, RoundedCornerShape(8.dp))
        )
        repeat(count) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .graphicsLayer { alpha = pulse }
                    .background(SportzfyColors.SlateRaised, RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
fun MessageState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = SportzfyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, SportzfyColors.TextMuted),
                modifier = Modifier.tvFocus(RoundedCornerShape(50)),
            ) { Text(actionLabel) }
        }
    }
}

/** Owner announcement from the remote config; tappable when it carries a link. */
@Composable
fun AnnouncementBanner(announcement: Announcement, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val link = announcement.linkUrl
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (link != null) {
                    Modifier.tvFocus(shape).clip(shape).clickable(role = Role.Button) { runCatching { uriHandler.openUri(link) } }
                } else {
                    Modifier
                }
            )
            .background(SportzfyColors.Emerald.copy(alpha = 0.14f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportzfyIcon(SportzfyIcons.Announcement, contentDescription = null, tint = SportzfyColors.Emerald, size = 18.dp)
        Text(
            announcement.message,
            style = MaterialTheme.typography.bodyMedium,
            color = SportzfyColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (link != null) {
            SportzfyIcon(SportzfyIcons.ExternalLink, contentDescription = "Open link", tint = SportzfyColors.Emerald, size = 16.dp)
        }
    }
}

/** Slim warning shown above content that is stale because a refresh failed. */
@Composable
fun InlineBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SportzfyColors.Rose.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportzfyIcon(SportzfyIcons.Alert, contentDescription = null, tint = SportzfyColors.Rose, size = 18.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SportzfyColors.TextPrimary)
    }
}

/**
 * Designed empty or error state: an icon, a title, a short message and up to
 * two actions (e.g. "Browse Today" / "See upcoming").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyState(
    @androidx.annotation.DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(SportzfyColors.SlateRaised, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SportzfyIcon(icon, contentDescription = null, tint = SportzfyColors.TextSecondary, size = 26.dp)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = SportzfyColors.TextPrimary, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SportzfyColors.TextSecondary, textAlign = TextAlign.Center)
        if (primaryAction != null || secondaryAction != null) {
            // Wraps to a second line instead of overflowing on 320dp screens.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                primaryAction?.let { (label, action) ->
                    androidx.compose.material3.Button(
                        onClick = action,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SportzfyColors.RoseFill),
                        modifier = Modifier.tvFocus(RoundedCornerShape(50)),
                    ) { Text(label) }
                }
                secondaryAction?.let { (label, action) ->
                    OutlinedButton(
                        onClick = action,
                        border = BorderStroke(1.dp, SportzfyColors.TextMuted),
                        modifier = Modifier.tvFocus(RoundedCornerShape(50)),
                    ) { Text(label) }
                }
            }
        }
    }
}
