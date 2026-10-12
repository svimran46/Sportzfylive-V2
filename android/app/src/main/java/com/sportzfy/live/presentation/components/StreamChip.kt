package com.sportzfy.live.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.presentation.model.WatchLogic

/** One stream option: "Stream 1 [HD] • English", with the source name below. */
@Composable
fun StreamChip(
    index: Int,
    stream: StreamItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .tvFocus(RoundedCornerShape(10.dp))
            .semantics { contentDescription = WatchLogic.streamLabel(index, stream) },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) SportzfyColors.Rose else SportzfyColors.SlateRaised,
        contentColor = SportzfyColors.TextPrimary,
        border = BorderStroke(1.dp, if (selected) SportzfyColors.Rose else SportzfyColors.Outline),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Stream ${index + 1}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                if (stream.isHd) {
                    Spacer(Modifier.width(6.dp))
                    HdTag()
                }
                if (stream.language.isNotBlank()) {
                    Text(
                        text = "  •  ${stream.language}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) SportzfyColors.TextPrimary else SportzfyColors.TextSecondary,
                    )
                }
            }
            Text(
                text = stream.source,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) SportzfyColors.TextPrimary.copy(alpha = 0.8f) else SportzfyColors.TextMuted,
            )
        }
    }
}
