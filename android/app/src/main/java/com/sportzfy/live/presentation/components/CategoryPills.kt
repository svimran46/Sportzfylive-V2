package com.sportzfy.live.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyIcons
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
    /** The drawer's "Other sports" filter is on: shown as its own selected pill. */
    otherSportsSelected: Boolean = false,
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
        if (otherSportsSelected) {
            item(key = "OTHER_SPORTS") {
                Pill(label = "Other sports", selected = true, onClick = {})
            }
        }
        items(options, key = { it.category?.name ?: "ALL" }) { option ->
            Pill(
                label = if (option.count > 0) "${option.label}  ${option.count}" else option.label,
                selected = !savedSelected && !otherSportsSelected && option.category == selected,
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
    SportzfyChip(
        label = label,
        selected = selected,
        onClick = onClick,
        leadingIcon = if (leadingStar) SportzfyIcons.Saved else null,
    )
}
