package com.sportzfy.live.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.sportzfy.live.core.theme.SportzfyColors

/** The SPORTZ**FY** wordmark used in every screen header. */
@Composable
fun SportzfyWordmark(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            append("SPORTZ")
            withStyle(SpanStyle(color = SportzfyColors.Rose)) { append("FY") }
        },
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
        modifier = modifier,
    )
}
