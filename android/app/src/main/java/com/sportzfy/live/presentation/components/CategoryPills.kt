package com.sportzfy.live.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.presentation.model.CategoryOption

/**
 * Filter row: "Saved" (bookmarked matches) followed by the sport pills.
 * Saved and a sport are mutually exclusive; the caller decides what a tap does.
 */
@Composable
fun CategoryPills(
    options: List<CategoryOption>,
    selected: SportCategory?,
    onSelect: (SportCategory?) -> Unit,
    modifier: Modifier = Modifier,
    savedCount: Int = 0,
    savedSelected: Boolean = false,
    onSelectSaved: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onSelectSaved != null) {
            item(key = "SAVED") {
                Pill(
                    label = if (savedCount > 0) "Saved  $savedCount" else "Saved",
                    selected = savedSelected,
                    onClick = onSelectSaved,
                    leadingStar = true,
                )
            }
        }
        items(options, key = { it.category?.name ?: "ALL" }) { option ->
            Pill(
                label = if (option.count > 0) "${option.label}  ${option.count}" else option.label,
                selected = !savedSelected && option.category == selected,
                onClick = { onSelect(option.category) },
            )
        }
    }
}

@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingStar: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (leadingStar) {
            { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = SportzfyColors.SlateRaised,
            labelColor = SportzfyColors.TextSecondary,
            iconColor = SportzfyColors.TextSecondary,
            selectedContainerColor = SportzfyColors.Rose,
            selectedLabelColor = SportzfyColors.TextPrimary,
            selectedLeadingIconColor = SportzfyColors.TextPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = SportzfyColors.Outline,
            selectedBorderColor = SportzfyColors.Rose,
        ),
    )
}
