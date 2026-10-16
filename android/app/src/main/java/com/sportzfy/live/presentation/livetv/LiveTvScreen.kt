package com.sportzfy.live.presentation.livetv

import androidx.annotation.DrawableRes
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.graphics.Color
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
        // Three columns on phones (100dp fits three from 340dp wide), more on tablets and TVs.
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
        // One compact toolbar row: search, country, sort. Then the chips.
        item(key = "toolbar", span = fullSpan) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelSearchField(state.query, actions.onQueryChange, Modifier.weight(1f))
                if (state.showCountries) CountryButton(state.selectedCountry, onPickCountry)
                SortToggle(
                    sort = state.sort,
                    onToggle = { actions.onSortChange(if (state.sort == ChannelSort.A_Z) ChannelSort.BROWSE else ChannelSort.A_Z) },
                )
            }
        }
        state.error?.let { error ->
            item(key = "banner", span = fullSpan) {
                InlineBanner("${error.userMessage()} Showing the last channels loaded.")
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
            .padding(start = if (onOpenMenu != null) 4.dp else 16.dp, end = 16.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenMenu != null) {
            IconButton(onClick = onOpenMenu, modifier = Modifier.tvFocus(CircleShape)) {
                SportzfyIcon(SportzfyIcons.Menu, contentDescription = "Open menu")
            }
            Spacer(Modifier.width(4.dp))
        }
        SportzfyIcon(SportzfyIcons.LiveTv, contentDescription = null, tint = SportzfyColors.Rose, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Live TV",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .testTag("screen-title")
                .semantics { heading() },
        )
    }
}

private val ToolbarHeight = 44.dp
private val ToolbarShape = RoundedCornerShape(12.dp)

/** Compact search box, the toolbar's height; Material's TextField is 56dp and underlined. */
@Composable
private fun ChannelSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = SportzfyColors.TextPrimary),
        cursorBrush = SolidColor(SportzfyColors.Rose),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier
            .height(ToolbarHeight)
            .clip(ToolbarShape)
            .background(SportzfyColors.SlateRaised)
            .border(1.dp, if (focused) SportzfyColors.Rose else SportzfyColors.Outline, ToolbarShape),
        decorationBox = { field ->
            Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SportzfyIcon(SportzfyIcons.Search, contentDescription = null, tint = SportzfyColors.TextMuted, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search channels", style = MaterialTheme.typography.bodyMedium, color = SportzfyColors.TextMuted, maxLines = 1)
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp).tvFocus(CircleShape)) {
                        SportzfyIcon(SportzfyIcons.Close, contentDescription = "Clear search", size = 18.dp)
                    }
                }
            }
        },
    )
}

/** "🌐 World ⌄": opens the country sheet. */
@Composable
private fun CountryButton(selected: CountryOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(ToolbarHeight)
            .widthIn(max = 132.dp)
            .tvFocus(ToolbarShape)
            .clip(ToolbarShape)
            .background(SportzfyColors.SlateRaised)
            .border(1.dp, SportzfyColors.Outline, ToolbarShape)
            .clickable(role = Role.Button, onClickLabel = "Change country", onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportzfyIcon(SportzfyIcons.Globe, contentDescription = null, tint = SportzfyColors.RoseText, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = selected.label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(2.dp))
        SportzfyIcon(SportzfyIcons.ChevronDown, contentDescription = null, tint = SportzfyColors.TextMuted, size = 16.dp)
    }
}

/** A–Z on (rose) or off (the playlist's own order). */
@Composable
private fun SortToggle(sort: ChannelSort, onToggle: () -> Unit) {
    val on = sort == ChannelSort.A_Z
    Box(
        modifier = Modifier
            .height(ToolbarHeight)
            .tvFocus(ToolbarShape)
            .clip(ToolbarShape)
            .background(if (on) SportzfyColors.RoseFill else SportzfyColors.SlateRaised)
            .border(1.dp, if (on) SportzfyColors.RoseFill else SportzfyColors.Outline, ToolbarShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = "Sort A to Z" }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ChannelSort.A_Z.label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (on) SportzfyColors.TextPrimary else SportzfyColors.TextSecondary,
        )
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
            PillChip(
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
                PillChip(
                    label = option.label,
                    selected = state.selectedShelf == null && option.category == state.selectedCategory,
                    onClick = { actions.onSelectCategory(option.category) },
                )
            }
        }
    }
}

/** 34dp pill: slimmer than the app's 48dp filter chip, so the grid starts higher. */
@Composable
private fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    @DrawableRes leadingIcon: Int? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(34.dp)
            .tvFocus(shape)
            .clip(shape)
            .background(if (selected) SportzfyColors.RoseFill else SportzfyColors.SlateRaised)
            .border(1.dp, if (selected) SportzfyColors.RoseFill else SportzfyColors.Outline, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            SportzfyIcon(
                leadingIcon,
                contentDescription = null,
                tint = if (selected) SportzfyColors.TextPrimary else SportzfyColors.TextMuted,
                size = 14.dp,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) SportzfyColors.TextPrimary else SportzfyColors.TextSecondary,
            maxLines = 1,
        )
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
                            .background(if (selected) SportzfyColors.SelectedSurface else Color.Transparent)
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
