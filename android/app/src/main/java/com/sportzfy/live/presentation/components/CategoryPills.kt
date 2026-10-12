package com.sportzfy.live.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.presentation.model.CategoryOption

@Composable
fun CategoryPills(
    options: List<CategoryOption>,
    selected: SportCategory?,
    onSelect: (SportCategory?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.category?.name ?: "ALL" }) { option ->
            val isSelected = option.category == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option.category) },
                label = { Text(if (option.count > 0) "${option.label}  ${option.count}" else option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SportzfyColors.SlateRaised,
                    labelColor = SportzfyColors.TextSecondary,
                    selectedContainerColor = SportzfyColors.Rose,
                    selectedLabelColor = SportzfyColors.TextPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = SportzfyColors.Outline,
                    selectedBorderColor = SportzfyColors.Rose,
                ),
            )
        }
    }
}
