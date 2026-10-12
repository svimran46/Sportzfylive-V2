package com.sportzfy.live.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.domain.model.Announcement

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SportzfyColors.Rose)
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
            OutlinedButton(onClick = onAction, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text(actionLabel) }
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
            .then(
                if (link != null) {
                    Modifier.tvFocus(shape).clickable { runCatching { uriHandler.openUri(link) } }
                } else {
                    Modifier
                }
            )
            .background(SportzfyColors.Emerald.copy(alpha = 0.14f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Campaign, contentDescription = null, tint = SportzfyColors.Emerald, modifier = Modifier.size(18.dp))
        Text(
            announcement.message,
            style = MaterialTheme.typography.bodyMedium,
            color = SportzfyColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (link != null) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open link", tint = SportzfyColors.Emerald, modifier = Modifier.size(16.dp))
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
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = SportzfyColors.Rose, modifier = Modifier.size(18.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = SportzfyColors.TextPrimary)
    }
}
