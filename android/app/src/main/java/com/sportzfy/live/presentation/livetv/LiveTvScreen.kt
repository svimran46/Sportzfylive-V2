package com.sportzfy.live.presentation.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.presentation.components.ChannelCard
import com.sportzfy.live.presentation.components.EmptyState
import com.sportzfy.live.presentation.components.InlineBanner
import com.sportzfy.live.presentation.components.MatchListSkeleton
import com.sportzfy.live.presentation.components.SegmentedControl
import com.sportzfy.live.presentation.components.SportzfyChip
import com.sportzfy.live.presentation.components.bleedHorizontally
import com.sportzfy.live.presentation.components.tvFocus
import com.sportzfy.live.presentation.model.ChannelShelf
import com.sportzfy.live.presentation.model.ChannelSort
import com.sportzfy.live.presentation.model.ChannelsUiState
import com.sportzfy.live.presentation.model.CountryOption
import com.sportzfy.live.presentation.model.userMessage
import org.koin.compose.viewmodel.koinViewModel

/**
 * Live TV: the player docked on top ([nowPlaying]) and the channel directory
 * under it. Picking a channel plays it in place; [onPlayChannel] gets the
 * grid it was picked from, which previous/next step through.
 */
@Composable
fun LiveTvScreen(
    onPlayChannel: (channelId: String, queue: List<String>) -> Unit,
    viewModel: LiveTvViewModel = koinViewModel(),
    playingChannelId: String? = null,
    nowPlaying: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    LiveTvContent(
        state = state,
        actions = LiveTvActions(
            onPlay = { id -> onPlayChannel(id, state.channels.map { it.id }) },
            onToggleFavorite = viewModel::toggleFavorite,
            onSelectCategory = viewModel::selectCategory,
            onSelectShelf = viewModel::selectShelf,
            onSelectCountry = viewModel::selectCountry,
            onSortChange = viewModel::setSort,
            onQueryChange = viewModel::setQuery,
            onRefresh = { viewModel.refresh(force = true) },
        ),
        playingChannelId = playingChannelId,
        nowPlaying = nowPlaying,
        bottomBar = bottomBar,
        onOpenMenu = onOpenMenu,
    )
}

/** Everything the directory can ask for. */
class LiveTvActions(
    val onPlay: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit = {},
    val onSelectCategory: (ChannelCategory?) -> Unit = {},
    val onSelectShelf: (ChannelShelf) -> Unit = {},
    val onSelectCountry: (String?) -> Unit = {},
    val onSortChange: (ChannelSort) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvContent(
    state: ChannelsUiState,
    actions: LiveTvActions,
    playingChannelId: String? = null,
    nowPlaying: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
) {
    var pickingCountry by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LiveTvHeader(onOpenMenu) },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Pinned above the grid, like a TV: the list scrolls, the picture stays.
            nowPlaying()
            PullToRefreshBox(
                isRefreshing = state.isRefreshing && state.hasData,
                onRefresh = actions.onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.isInitialLoading || (!state.hasData && state.isRefreshing) ->
                        MatchListSkeleton(contentDescription = "Loading channels")
                    !state.hasData && state.error != null -> EmptyState(
                        icon = SportzfyIcons.Offline,
                        title = "Couldn't load channels",
                        message = state.error.userMessage(),
                        primaryAction = "Retry" to actions.onRefresh,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> ChannelGrid(state, actions, playingChannelId, onPickCountry = { pickingCountry = true })
                }
            }
        }
    }

    if (pickingCountry) {
        CountrySheet(
            state = state,
            onSelect = { country ->
                actions.onSelectCountry(country)
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false },
        )
    }
}

@Composable
private fun ChannelGrid(
    state: ChannelsUiState,
    actions: LiveTvActions,
    playingChannelId: String?,
    onPickCountry: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
        item(key = "search", span = fullSpan) {
            ChannelSearchField(state.query, actions.onQueryChange)
        }
        state.error?.let { error ->
            item(key = "banner", span = fullSpan) {
                InlineBanner("${error.userMessage()} Showing the last channels loaded.")
            }
        }
        item(key = "toolbar", span = fullSpan) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.showCountries) {
                    CountryButton(state.selectedCountry, onPickCountry, Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                SegmentedControl(
                    options = ChannelSort.entries.map { it.label },
                    selectedIndex = state.sort.ordinal,
                    onSelect = { actions.onSortChange(ChannelSort.entries[it]) },
                    modifier = Modifier.width(170.dp),
                )
            }
        }
        if (state.showCategories || state.shelves.isNotEmpty()) {
            item(key = "chips", span = fullSpan) {
                FilterChips(state, actions)
            }
        }
        if (state.channels.isEmpty()) {
            item(key = "empty", span = fullSpan) {
                EmptyGrid(state, actions)
            }
        }
        items(state.channels, key = { "channel-${it.id}" }) { channel ->
            ChannelCard(
                channel = channel,
                onClick = { actions.onPlay(channel.id) },
                isPlaying = channel.id == playingChannelId,
                isFavorite = channel.id in state.favoriteIds,
                onToggleFavorite = { actions.onToggleFavorite(channel.id) },
            )
        }
    }
}

@Composable
private fun EmptyGrid(state: ChannelsUiState, actions: LiveTvActions) {
    when {
        state.query.isNotBlank() -> EmptyState(
            icon = SportzfyIcons.Search,
            title = "No channels match \"${state.query.trim()}\"",
            message = "Try another name, or clear the search.",
            primaryAction = "Clear search" to { actions.onQueryChange("") },
        )
        state.selectedShelf == ChannelShelf.FAVORITES -> EmptyState(
            icon = SportzfyIcons.Star,
            title = "No favorites yet",
            message = "Tap the star on a channel to keep it here.",
            primaryAction = "Browse all channels" to { actions.onSelectCategory(null) },
        )
        state.selectedShelf == ChannelShelf.RECENT -> EmptyState(
            icon = SportzfyIcons.Clock,
            title = "Nothing watched yet",
            message = "Channels you play show up here.",
            primaryAction = "Browse all channels" to { actions.onSelectCategory(null) },
        )
        else -> EmptyState(
            icon = SportzfyIcons.LiveTv,
            title = "No channels right now",
            message = "The channel list is empty. Try again in a little while.",
            primaryAction = "Refresh" to actions.onRefresh,
        )
    }
}

@Composable
private fun LiveTvHeader(onOpenMenu: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
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
        placeholder = { Text("Search channels…") },
        leadingIcon = { SportzfyIcon(SportzfyIcons.Search, contentDescription = null, size = 22.dp) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.tvFocus(CircleShape)) {
                    SportzfyIcon(SportzfyIcons.Close, contentDescription = "Clear search", size = 22.dp)
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(16.dp),
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

/** "COUNTRY  World · 2230  ⌄": opens the country sheet. */
@Composable
private fun CountryButton(selected: CountryOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .tvFocus(shape)
            .clip(shape)
            .background(SportzfyColors.SlateRaised)
            .border(1.dp, SportzfyColors.Outline, shape)
            .clickable(role = Role.Button, onClickLabel = "Change country", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "COUNTRY",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
            color = SportzfyColors.TextMuted,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${selected.label} · ${selected.count}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SportzfyIcon(SportzfyIcons.ChevronDown, contentDescription = null, tint = SportzfyColors.TextSecondary, size = 18.dp)
    }
}

/** Recent and Favorites first (once they hold something), then All and each sport. */
@Composable
private fun FilterChips(state: ChannelsUiState, actions: LiveTvActions) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.bleedHorizontally(16.dp),
    ) {
        items(state.shelves, key = { "shelf-${it.name}" }) { shelf ->
            SportzfyChip(
                label = shelf.label,
                selected = state.selectedShelf == shelf,
                onClick = { actions.onSelectShelf(shelf) },
                leadingIcon = when (shelf) {
                    ChannelShelf.RECENT -> SportzfyIcons.Clock
                    ChannelShelf.FAVORITES -> SportzfyIcons.Star
                },
            )
        }
        if (state.showCategories) {
            items(state.categories, key = { it.category?.name ?: "all" }) { option ->
                SportzfyChip(
                    label = option.label,
                    selected = state.selectedShelf == null && option.category == state.selectedCategory,
                    onClick = { actions.onSelectCategory(option.category) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(state: ChannelsUiState, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val options = remember(state.countries, filter) {
        val needle = filter.trim().lowercase()
        state.countries.filter { needle.isEmpty() || it.label.lowercase().contains(needle) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SportzfyColors.Slate,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Country", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            TextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Find a country") },
                leadingIcon = { SportzfyIcon(SportzfyIcons.Search, contentDescription = null, size = 20.dp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SportzfyColors.SlateRaised,
                    unfocusedContainerColor = SportzfyColors.SlateRaised,
                    focusedIndicatorColor = SportzfyColors.Rose,
                    unfocusedIndicatorColor = SportzfyColors.Outline,
                    cursorColor = SportzfyColors.Rose,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(options, key = { it.name ?: "world" }) { option ->
                    val selected = option.name == state.selectedCountry.name
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .tvFocus(shape, focusedScale = 1f)
                            .clip(shape)
                            .background(if (selected) SportzfyColors.SelectedSurface else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(role = Role.Button) { onSelect(option.name) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SportzfyIcon(
                            if (option.name == null) SportzfyIcons.Globe else SportzfyIcons.LiveTv,
                            contentDescription = null,
                            tint = if (selected) SportzfyColors.Rose else SportzfyColors.TextMuted,
                            size = 18.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = option.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = SportzfyColors.TextMuted,
                        )
                    }
                }
            }
        }
    }
}
