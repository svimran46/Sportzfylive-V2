package com.sportzfy.live.presentation.components

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.ScoreTextStyle
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.Team
import com.sportzfy.live.presentation.model.MatchTimeFormatter
import com.sportzfy.live.presentation.model.sourcesLabel

@Composable
fun MatchCard(
    match: Match,
    nowMillis: Long,
    formatter: MatchTimeFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier.tvFocus(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        // Explicit content colour: titles and team names are white, not the grey M3 default.
        colors = CardDefaults.cardColors(containerColor = SportzfyColors.SlateRaised, contentColor = SportzfyColors.TextPrimary),
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.category.displayName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = SportzfyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusTag(match, nowMillis, formatter)
                if (onToggleFavorite != null) {
                    // 40dp visual with a 48dp touch target; nudged right to keep the row compact.
                    FavoriteButton(isFavorite, onToggleFavorite, Modifier.size(40.dp).offset(x = 8.dp))
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

            val home = match.homeTeam
            val away = match.awayTeam
            if (home != null && away != null) {
                TeamRow(home, compact)
                Spacer(Modifier.height(6.dp))
                TeamRow(away, compact)
            } else {
                Text(
                    text = match.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!compact) {
                Spacer(Modifier.height(12.dp))
                FeedChip(sourcesLabel(match.sources.size))
            }
        }
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .tvFocus(CircleShape)
            .semantics { stateDescription = if (isFavorite) "Saved" else "Not saved" },
    ) {
        // Lucide is outline-only: the saved state is the accent colour.
        SportzfyIcon(
            SportzfyIcons.Saved,
            contentDescription = if (isFavorite) "Remove from saved" else "Save match",
            tint = if (isFavorite) SportzfyColors.Rose else SportzfyColors.TextMuted,
            size = 22.dp,
        )
    }
}

@Composable
fun StatusTag(match: Match, nowMillis: Long, formatter: MatchTimeFormatter, modifier: Modifier = Modifier) {
    if (match.isLive) {
        LiveBadge(modifier)
    } else {
        Text(
            text = formatter.status(match, nowMillis),
            style = ScoreTextStyle,
            color = SportzfyColors.TextSecondary,
            modifier = modifier,
        )
    }
}

@Composable
private fun TeamRow(team: Team, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamCrest(team, size = if (compact) 22.dp else 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = team.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FeedChip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(SportzfyColors.Outline.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = SportzfyColors.TextSecondary)
    }
}

/** Fixed-width card for horizontal rails. */
@Composable
fun MatchRailCard(
    match: Match,
    nowMillis: Long,
    formatter: MatchTimeFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MatchCard(match, nowMillis, formatter, onClick, modifier.width(220.dp), compact = true)
}
