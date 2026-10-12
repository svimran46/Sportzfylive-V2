package com.sportzfy.live.presentation.components

import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon

/** One destination in [BottomNavBar]. */
data class NavBarItem(val key: String, val label: String, @DrawableRes val icon: Int)

/**
 * Fixed bottom navigation. Sits above the system navigation bar; the active
 * item gets the pink accent and a short indicator line.
 */
@Composable
fun BottomNavBar(
    items: List<NavBarItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SportzfyColors.Slate)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(color = SportzfyColors.Outline, thickness = 1.dp)
        Row(Modifier.fillMaxWidth()) {
            items.forEach { item ->
                BottomNavItem(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    item: NavBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) SportzfyColors.Rose else SportzfyColors.TextSecondary
    Column(
        modifier = modifier
            .heightIn(min = 60.dp)
            .tvFocus(RoundedCornerShape(12.dp), focusedScale = 1f)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(3.dp)
                .background(if (selected) SportzfyColors.Rose else Color.Transparent, RoundedCornerShape(50))
        )
        SportzfyIcon(item.icon, contentDescription = null, tint = color, size = 22.dp)
        Text(
            text = item.label,
            // Pink text uses the lighter text shade for contrast; the icon keeps Rose.
            color = if (selected) SportzfyColors.RoseText else SportzfyColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A navigation row in the side drawer. The active row gets a subtle selected
 * surface, a pink accent bar and a pink icon; pink stays an accent.
 */
@Composable
fun DrawerRow(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .tvFocus(shape, focusedScale = 1f)
            .clip(shape)
            .background(if (selected) SportzfyColors.SelectedSurface else Color.Transparent)
            .drawBehind {
                if (selected) {
                    val barHeight = size.height * 0.5f
                    drawRoundRect(
                        color = SportzfyColors.Rose,
                        topLeft = Offset(0f, (size.height - barHeight) / 2),
                        size = Size(3.dp.toPx(), barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportzfyIcon(
            icon,
            contentDescription = null,
            tint = if (selected) SportzfyColors.Rose else SportzfyColors.TextSecondary,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 15.sp,
            ),
            color = SportzfyColors.TextPrimary,
        )
    }
}

/** Small uppercase heading that groups drawer rows (e.g. "SPORTS"). */
@Composable
fun DrawerSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
        color = SportzfyColors.RoseText,
        modifier = modifier
            .padding(start = 14.dp, top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}
