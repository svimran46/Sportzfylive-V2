package com.sportzfy.live.presentation.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.presentation.components.ChannelCard
import com.sportzfy.live.presentation.components.EmptyState
import com.sportzfy.live.presentation.components.InlineBanner
import com.sportzfy.live.presentation.components.MatchListSkeleton
import com.sportzfy.live.presentation.components.SportzfyChip
import com.sportzfy.live.presentation.components.tvFocus
import com.sportzfy.live.presentation.model.ChannelsUiState
import com.sportzfy.live.presentation.model.userMessage
import org.koin.compose.viewmodel.koinViewModel

/** Live TV: the 24/7 channel directory, separate from match fixtures. */
@Composable
fun LiveTvScreen(
    onOpenChannel: (String) -> Unit,
    viewModel: LiveTvViewModel = koinViewModel(),
    bottomBar: @Composable () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    LiveTvContent(
        state = state,
        onOpenChannel = onOpenChannel,
        onSelectCategory = viewModel::selectCategory,
        onQueryChange = viewModel::setQuery,
        onRefresh = { viewModel.refresh(force = true) },
        bottomBar = bottomBar,
        onOpenMenu = onOpenMenu,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvContent(
    state: ChannelsUiState,
    onOpenChannel: (String) -> Unit,
    onSelectCategory: (ChannelCategory?) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LiveTvHeader(onOpenMenu) },
        bottomBar = bottomBar,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && state.hasData,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                state.isInitialLoading || (!state.hasData && state.isRefreshing) ->
                    MatchListSkeleton(contentDescription = "Loading channels")
                !state.hasData && state.error != null -> EmptyState(
                    icon = SportzfyIcons.Offline,
                    title = "Couldn't load channels",
                    message = state.error.userMessage(),
                    primaryAction = "Retry" to onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
                    item(key = "search", span = fullSpan) {
                        ChannelSearchField(state.query, onQueryChange)
                    }
                    state.error?.let { error ->
                        item(key = "banner", span = fullSpan) {
                            InlineBanner("${error.userMessage()} Showing the last channels loaded.")
                        }
                    }
                    if (state.showCategories) {
                        item(key = "categories", span = fullSpan) {
                            CategoryChips(state, onSelectCategory)
                        }
                    }
                    if (state.channels.isEmpty()) {
                        item(key = "empty", span = fullSpan) {
                            if (state.query.isNotBlank()) {
                                EmptyState(
                                    icon = SportzfyIcons.Search,
                                    title = "No channels match \"${state.query.trim()}\"",
                                    message = "Try another name, or clear the search.",
                                    primaryAction = "Clear search" to { onQueryChange("") },
                                )
                            } else {
                                EmptyState(
                                    icon = SportzfyIcons.LiveTv,
                                    title = "No channels right now",
                                    message = "The channel list is empty. Try again in a little while.",
                                    primaryAction = "Refresh" to onRefresh,
                                )
                            }
                        }
                    }
                    items(state.channels, key = { "channel-${it.id}" }) { channel ->
                        ChannelCard(channel, onClick = { onOpenChannel(channel.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTvHeader(onOpenMenu: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = if (onOpenMenu != null) 4.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenMenu != null) {
            IconButton(onClick = onOpenMenu, modifier = Modifier.tvFocus(CircleShape)) {
                SportzfyIcon(SportzfyIcons.Menu, contentDescription = "Open menu")
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SportzfyColors.Rose.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SportzfyIcon(SportzfyIcons.LiveTv, contentDescription = null, tint = SportzfyColors.Rose, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Live TV",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .testTag("screen-title")
                .semantics { heading() },
        )
    }
}

@Composable
private fun ChannelSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search channels") },
        leadingIcon = { SportzfyIcon(SportzfyIcons.Search, contentDescription = null, size = 20.dp) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.tvFocus(CircleShape)) {
                    SportzfyIcon(SportzfyIcons.Close, contentDescription = "Clear search", size = 20.dp)
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SportzfyColors.SlateRaised,
            unfocusedContainerColor = SportzfyColors.SlateRaised,
            focusedIndicatorColor = SportzfyColors.Rose,
            unfocusedIndicatorColor = SportzfyColors.Outline,
            cursorColor = SportzfyColors.Rose,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryChips(state: ChannelsUiState, onSelect: (ChannelCategory?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.categories, key = { it.category?.name ?: "all" }) { option ->
            SportzfyChip(
                label = "${option.label}  ${option.count}",
                selected = option.category == state.selectedCategory,
                onClick = { onSelect(option.category) },
            )
        }
    }
}
