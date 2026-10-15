package com.sportzfy.live.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Lets a child of a padded list reach the screen edges: it is measured
 * [bleed] wider on both sides and placed so its edges line up with the
 * parent's. Used for horizontal chip rows, which then scroll edge to edge
 * (pair it with contentPadding = [bleed] to keep the first chip aligned).
 */
fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + 2 * extra,
            maxWidth = constraints.maxWidth + 2 * extra,
        )
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
