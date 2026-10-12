package com.sportzfy.live.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyColors

/**
 * D-pad focus treatment for Android TV and keyboards: a 2dp rose border and
 * a slight scale-up while the element itself has focus. A focused child
 * (e.g. the star on a card) highlights alone, never together with its parent.
 *
 * Apply it to the `modifier` of a clickable component (Card, chip, button).
 * Those are already focus targets, so no extra `focusable()` is added; that
 * would create a second, invisible focus stop.
 */
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.03f,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focusScale")
    this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(
            width = 2.dp,
            color = if (focused) SportzfyColors.Rose else Color.Transparent,
            shape = shape,
        )
}
