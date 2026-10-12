package com.sportzfy.live.presentation.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.presentation.components.EmptyState
import com.sportzfy.live.presentation.components.SportzfyWordmark

/**
 * Live TV destination. Routing stub for the bottom bar: the channel
 * directory replaces this placeholder in the next phase.
 */
@Composable
fun LiveTvScreen(bottomBar: @Composable () -> Unit = {}) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp),
                contentAlignment = Alignment.CenterStart,
            ) { SportzfyWordmark() }
        },
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = SportzfyIcons.LiveTv,
                title = "Live TV is coming",
                message = "The channel directory arrives with the next update.",
            )
        }
    }
}
