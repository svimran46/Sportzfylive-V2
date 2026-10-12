package com.sportzfy.live.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.presentation.model.MatchTimeFormatter

/** Large banner for the featured match, with a direct "Watch" action. */
@Composable
fun HeroSpotlight(
    match: Match,
    nowMillis: Long,
    formatter: MatchTimeFormatter,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onWatch,
        modifier = modifier.fillMaxWidth().height(220.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = SportzfyColors.SlateRaised),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(SportzfyColors.RoseDim, SportzfyColors.Slate)))
            )
            if (match.posterUrl != null) {
                AsyncImage(
                    model = match.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to SportzfyColors.Charcoal.copy(alpha = 0.35f),
                            1f to SportzfyColors.Charcoal.copy(alpha = 0.95f),
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusTag(match, nowMillis, formatter)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = match.category.displayName.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = SportzfyColors.TextSecondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.hasTeams) {
                        TeamCrest(match.homeTeam, size = 30.dp)
                        Spacer(Modifier.width(6.dp))
                        TeamCrest(match.awayTeam, size = 30.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = match.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Button(
                    onClick = onWatch,
                    colors = ButtonDefaults.buttonColors(containerColor = SportzfyColors.Rose),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watch")
                }
            }
        }
    }
}
