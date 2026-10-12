package com.sportzfy.live.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon

/** Filter chip: pink when selected, outlined surface otherwise. */
@Composable
fun SportzfyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.tvFocus(shape),
        shape = shape,
        color = if (selected) SportzfyColors.Rose else SportzfyColors.SlateRaised,
        contentColor = if (selected) SportzfyColors.TextPrimary else SportzfyColors.TextSecondary,
        border = BorderStroke(1.dp, if (selected) SportzfyColors.Rose else SportzfyColors.Outline),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                SportzfyIcon(leadingIcon, contentDescription = null, size = 16.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        }
    }
}

/** Compact switch between a few views, e.g. Live | Upcoming | All. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = RoundedCornerShape(12.dp)
    val inner = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .background(SportzfyColors.SlateRaised, outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .tvFocus(inner, focusedScale = 1f)
                    .background(if (selected) SportzfyColors.Outline else Color.Transparent, inner)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) SportzfyColors.TextPrimary else SportzfyColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
