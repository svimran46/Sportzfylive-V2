package com.sportzfy.live.presentation.catalog

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sportzfy.live.core.theme.ScoreTextStyle
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.ClockFormat
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.presentation.components.CategoryPills
import com.sportzfy.live.presentation.components.EmptyState
import com.sportzfy.live.presentation.components.MatchListSkeleton
import com.sportzfy.live.presentation.components.SegmentedControl
import com.sportzfy.live.presentation.components.HeroSpotlight
import com.sportzfy.live.presentation.components.AnnouncementBanner
import com.sportzfy.live.presentation.components.InlineBanner
import com.sportzfy.live.presentation.components.MatchCard
import com.sportzfy.live.presentation.components.MessageState
import com.sportzfy.live.presentation.components.PulsingDot
import com.sportzfy.live.presentation.components.SportzfyWordmark
import com.sportzfy.live.presentation.components.tvFocus
import com.sportzfy.live.presentation.model.CatalogUiState
import com.sportzfy.live.presentation.model.DayScope
import com.sportzfy.live.presentation.model.HomeView
import com.sportzfy.live.presentation.model.MatchTimeFormatter
import com.sportzfy.live.presentation.model.userMessage
import com.sportzfy.live.presentation.settings.LocalUserSettings
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import java.util.TimeZone

private const val AUTO_REFRESH_MILLIS = 60_000L

/**
 * One fixture list screen. The Today, Tomorrow and All Matches tabs are this
 * screen with a different [scope], sharing one [viewModel].
 */
@Composable
fun MatchCatalogScreen(
    scope: DayScope,
    onOpenMatch: (String) -> Unit,
    viewModel: CatalogViewModel = koinViewModel(),
    bottomBar: @Composable () -> Unit = {},
    onBrowseTomorrow: () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
) {
    val state by viewModel.state(scope).collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Poll while the screen is visible so live badges stay current.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refresh()
                delay(AUTO_REFRESH_MILLIS)
            }
        }
    }

    MatchCatalogContent(
        state = state,
        onOpenMatch = onOpenMatch,
        onSelectCategory = viewModel::selectCategory,
        onSelectSaved = viewModel::showFavorites,
        onToggleFavorite = viewModel::toggleFavorite,
        onQueryChange = viewModel::setQuery,
        onRefresh = { viewModel.refresh(force = true) },
        bottomBar = bottomBar,
        onSelectHomeView = viewModel::setHomeView,
        onBrowseTomorrow = onBrowseTomorrow,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onOpenMenu = onOpenMenu,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchCatalogContent(
    state: CatalogUiState,
    onOpenMatch: (String) -> Unit,
    onSelectCategory: (SportCategory?) -> Unit,
    onSelectSaved: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    onSelectHomeView: (HomeView) -> Unit = {},
    onBrowseTomorrow: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
) {
    val formatter = rememberMatchTimeFormatter()
    val now by rememberNow()

    // Spinners are for refreshes the user asked for, not the silent 60s poll.
    var userRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.isRefreshing) { if (!state.isRefreshing) userRefreshing = false }
    val requestRefresh: () -> Unit = {
        userRefreshing = true
        onRefresh()
    }

    // Back closes the search field first (registered after the NavHost, so it wins).
    BackHandler(enabled = state.searchActive) { onCloseSearch() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CatalogHeader(
                // Home has its own live heading, so the counter would repeat it.
                liveCount = state.liveCount.takeIf { state.homeView == null },
                // Shared by every fixture tab, so the drawer's Search can open it.
                searchOpen = state.searchActive,
                query = state.query,
                onQueryChange = onQueryChange,
                onOpenSearch = onOpenSearch,
                onCloseSearch = onCloseSearch,
                onOpenMenu = onOpenMenu,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = userRefreshing && state.isRefreshing && state.hasData,
            onRefresh = requestRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                // A Retry after a failed first load shows the skeleton while it runs.
                state.isInitialLoading || (!state.hasData && state.isRefreshing) -> MatchListSkeleton()
                !state.hasData && state.error != null -> MessageState(
                    message = state.error.userMessage(),
                    actionLabel = "Retry",
                    onAction = requestRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    catalogItems(
                        state, now, formatter, onOpenMatch, onSelectCategory, onSelectSaved, onToggleFavorite,
                        requestRefresh, onSelectHomeView, onBrowseTomorrow,
                        refreshing = userRefreshing && state.isRefreshing,
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.catalogItems(
    state: CatalogUiState,
    now: Long,
    formatter: MatchTimeFormatter,
    onOpenMatch: (String) -> Unit,
    onSelectCategory: (SportCategory?) -> Unit,
    onSelectSaved: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelectHomeView: (HomeView) -> Unit,
    onBrowseTomorrow: () -> Unit,
    refreshing: Boolean,
) {
    val fullSpan: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }

    val title = when (state.scope) {
        DayScope.TOMORROW -> "Tomorrow"
        DayScope.ALL -> "All Matches"
        else -> null
    }
    if (title != null) {
        item(key = "title", span = fullSpan) {
            ScreenTitle(title, state.dateLabel)
        }
    }
    state.announcement?.let { announcement ->
        item(key = "announcement", span = fullSpan) {
            AnnouncementBanner(announcement)
        }
    }
    state.error?.let { error ->
        item(key = "banner", span = fullSpan) {
            InlineBanner("${error.userMessage()} Showing the last fixtures loaded.")
        }
    }
    // An empty Live view says this itself, with a Retry button.
    val liveEmptyExplains = state.homeView == HomeView.LIVE && state.isEmpty
    if (!state.liveStatusKnown && state.error == null && !liveEmptyExplains) {
        item(key = "live-unknown", span = fullSpan) {
            InlineBanner("Couldn't check which matches are live, so none are marked LIVE. Refresh to try again.")
        }
    }
    state.homeView?.let { view ->
        item(key = "home-heading", span = fullSpan) {
            HomeHeading(view, state.listCount, countKnown = view != HomeView.LIVE || state.liveStatusKnown, state.dateLabel)
        }
        item(key = "home-controls", span = fullSpan) {
            HomeControls(view, refreshing, onSelectHomeView, onRefresh)
        }
    }
    state.hero?.let { hero ->
        item(key = "hero", span = fullSpan) {
            HeroSpotlight(hero, now, formatter, onWatch = { onOpenMatch(hero.id) })
        }
    }
    item(key = "categories", span = fullSpan) {
        CategoryPills(
            options = state.categories,
            selected = state.selectedCategory,
            onSelect = onSelectCategory,
            savedCount = state.savedCount,
            savedSelected = state.favoritesOnly,
            onSelectSaved = onSelectSaved,
            otherSportsSelected = state.otherSports,
        )
    }
    val homeView = state.homeView
    if (state.isEmpty && homeView != null && state.query.isBlank() && !state.favoritesOnly) {
        item(key = "empty", span = fullSpan) {
            HomeEmptyState(state, homeView, onSelectHomeView, onSelectCategory, onBrowseTomorrow, onRefresh)
        }
    } else if (state.isEmpty && state.query.isBlank() && !state.favoritesOnly &&
        (state.selectedCategory != null || state.otherSports)
    ) {
        // A sport filter carried over from another tab: say so and offer a way out.
        item(key = "empty", span = fullSpan) {
            val name = state.selectedCategory?.displayName ?: "other sports"
            EmptyState(
                icon = if (state.otherSports) SportzfyIcons.OtherSports else SportzfyIcons.Football,
                title = "Nothing in $name",
                message = when (state.scope) {
                    DayScope.TOMORROW -> "No $name fixtures are announced for tomorrow."
                    else -> "No $name fixtures right now."
                },
                primaryAction = "All sports" to { onSelectCategory(null) },
            )
        }
    } else if (state.isEmpty) {
        item(key = "empty", span = fullSpan) {
            MessageState(
                when {
                    state.query.isNotBlank() -> "No matches for \"${state.query.trim()}\"."
                    state.favoritesOnly -> "No saved matches here. Tap the star on a match to save it."
                    state.scope == DayScope.TOMORROW -> "No fixtures announced for tomorrow yet."
                    state.scope == DayScope.ALL -> "No fixtures available right now."
                    else -> "No fixtures in this category right now."
                }
            )
        }
    }
    state.sections.forEach { section ->
        if (section.title.isNotEmpty()) {
            item(key = "section-${section.key}", span = fullSpan) {
                SectionHeader(section.title, section.matches.size)
            }
        }
        items(section.matches, key = { "match-${it.id}" }) { match ->
            MatchCard(
                match = match,
                nowMillis = now,
                formatter = formatter,
                onClick = { onOpenMatch(match.id) },
                isFavorite = match.id in state.favoriteIds,
                onToggleFavorite = { onToggleFavorite(match.id) },
            )
        }
    }
}

@Composable
private fun CatalogHeader(
    liveCount: Int?,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenMenu: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = if (onOpenMenu != null) 4.dp else 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchOpen) {
            val focusRequester = remember { FocusRequester() }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search teams, leagues or sports") },
                leadingIcon = { SportzfyIcon(SportzfyIcons.Search, contentDescription = null, size = 20.dp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SportzfyColors.SlateRaised,
                    unfocusedContainerColor = SportzfyColors.SlateRaised,
                    focusedIndicatorColor = SportzfyColors.Rose,
                    unfocusedIndicatorColor = SportzfyColors.Outline,
                    cursorColor = SportzfyColors.Rose,
                ),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            IconButton(onClick = onCloseSearch, modifier = Modifier.tvFocus(CircleShape)) {
                SportzfyIcon(SportzfyIcons.Close, contentDescription = "Close search")
            }
            // Only an empty field grabs focus, so returning to a tab with a
            // search already typed doesn't pop the keyboard up again.
            LaunchedEffect(Unit) { if (query.isEmpty()) focusRequester.requestFocus() }
        } else {
            if (onOpenMenu != null) {
                IconButton(onClick = onOpenMenu, modifier = Modifier.tvFocus(CircleShape)) {
                    SportzfyIcon(SportzfyIcons.Menu, contentDescription = "Open menu")
                }
                Spacer(Modifier.width(4.dp))
            }
            SportzfyWordmark()
            if (liveCount != null) {
                Spacer(Modifier.width(12.dp))
                LiveCounter(liveCount)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSearch, modifier = Modifier.tvFocus(CircleShape)) {
                SportzfyIcon(SportzfyIcons.Search, contentDescription = "Search")
            }
        }
    }
}

@Composable
private fun LiveCounter(count: Int) {
    Row(
        modifier = Modifier
            .background(SportzfyColors.SlateRaised, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count > 0) PulsingDot() else PulsingDotPlaceholder()
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count LIVE",
            style = ScoreTextStyle,
            color = if (count > 0) SportzfyColors.TextPrimary else SportzfyColors.TextMuted,
        )
    }
}

@Composable
private fun PulsingDotPlaceholder() {
    Spacer(
        Modifier
            .width(8.dp)
            .height(8.dp)
            .background(SportzfyColors.TextMuted, RoundedCornerShape(50))
    )
}

@Composable
private fun ScreenTitle(title: String, dateLabel: String?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("screen-title"),
        )
        if (dateLabel != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = SportzfyColors.TextSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** "Live Matches  ● 3 LIVE", "Upcoming Today  4" or "Today  Mon 12 Oct  9". */
@Composable
private fun HomeHeading(view: HomeView, count: Int, countKnown: Boolean, dateLabel: String?) {
    val title = when (view) {
        HomeView.LIVE -> "Live Matches"
        HomeView.UPCOMING -> "Upcoming Today"
        HomeView.ALL -> "Today"
    }
    Row(
        modifier = Modifier
            .testTag("screen-title")
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (view == HomeView.ALL && dateLabel != null) {
            Spacer(Modifier.width(10.dp))
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = SportzfyColors.TextSecondary)
        }
        // Counts only when the data behind them is reliable.
        if (countKnown) {
            Spacer(Modifier.width(10.dp))
            if (view == HomeView.LIVE) LiveCounter(count) else CountPill(count)
        }
    }
}

@Composable
private fun CountPill(count: Int) {
    Text(
        text = "$count",
        style = ScoreTextStyle,
        color = SportzfyColors.TextSecondary,
        modifier = Modifier
            .background(SportzfyColors.SlateRaised, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Live | Upcoming | All, plus Refresh with its own progress state. */
@Composable
private fun HomeControls(
    view: HomeView,
    refreshing: Boolean,
    onSelect: (HomeView) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SegmentedControl(
            options = HomeView.entries.map { it.label },
            selectedIndex = view.ordinal,
            onSelect = { onSelect(HomeView.entries[it]) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        val shape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .size(48.dp)
                .tvFocus(shape)
                .background(SportzfyColors.SlateRaised, shape)
                // Disabled while a refresh runs, so taps can't stack requests.
                .clickable(enabled = !refreshing, role = Role.Button, onClick = onRefresh)
                .semantics { contentDescription = if (refreshing) "Refreshing" else "Refresh" },
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(20.dp), color = SportzfyColors.Rose, strokeWidth = 2.dp)
            } else {
                SportzfyIcon(SportzfyIcons.Refresh, contentDescription = null, size = 20.dp)
            }
        }
    }
}

/** Never "nothing here" alone: each empty Home view offers the next place to look. */
@Composable
private fun HomeEmptyState(
    state: CatalogUiState,
    view: HomeView,
    onSelectHomeView: (HomeView) -> Unit,
    onSelectCategory: (SportCategory?) -> Unit,
    onBrowseTomorrow: () -> Unit,
    onRefresh: () -> Unit,
) {
    val category = state.selectedCategory
    when {
        category != null -> EmptyState(
            icon = SportzfyIcons.Football,
            title = "Nothing in ${category.displayName}",
            message = when (view) {
                HomeView.LIVE -> "No ${category.displayName} matches are live right now."
                HomeView.UPCOMING -> "No more ${category.displayName} kick-offs today."
                HomeView.ALL -> "No ${category.displayName} fixtures today."
            },
            primaryAction = "All sports" to { onSelectCategory(null) },
        )
        state.otherSports -> EmptyState(
            icon = SportzfyIcons.OtherSports,
            title = "Nothing in other sports",
            message = when (view) {
                HomeView.LIVE -> "No sports besides football and cricket are live right now."
                HomeView.UPCOMING -> "No more kick-offs in other sports today."
                HomeView.ALL -> "No fixtures in other sports today."
            },
            primaryAction = "All sports" to { onSelectCategory(null) },
        )
        view == HomeView.LIVE && !state.liveStatusKnown -> EmptyState(
            icon = SportzfyIcons.Alert,
            title = "Can't check live matches",
            message = "The live feed didn't respond, so nothing is marked live. Try again in a moment.",
            primaryAction = "Retry" to onRefresh,
            secondaryAction = "Upcoming" to { onSelectHomeView(HomeView.UPCOMING) },
        )
        view == HomeView.LIVE -> EmptyState(
            icon = SportzfyIcons.LiveTv,
            title = "No live matches right now",
            message = "Nothing is on air at the moment. See what kicks off later today.",
            primaryAction = "Upcoming" to { onSelectHomeView(HomeView.UPCOMING) },
            secondaryAction = "All today" to { onSelectHomeView(HomeView.ALL) },
        )
        view == HomeView.UPCOMING -> EmptyState(
            icon = SportzfyIcons.Clock,
            title = "Nothing else today",
            message = "No more kick-offs are scheduled for today.",
            primaryAction = "Tomorrow" to onBrowseTomorrow,
            secondaryAction = "All today" to { onSelectHomeView(HomeView.ALL) },
        )
        else -> EmptyState(
            icon = SportzfyIcons.Today,
            title = "No fixtures today",
            message = "Nothing is scheduled for today yet.",
            primaryAction = "Tomorrow" to onBrowseTomorrow,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = ScoreTextStyle, color = SportzfyColors.TextMuted)
    }
}

@Composable
internal fun rememberMatchTimeFormatter(): MatchTimeFormatter {
    val context = LocalContext.current
    val use24Hour = when (LocalUserSettings.current.clockFormat) {
        ClockFormat.H12 -> false
        ClockFormat.H24 -> true
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    // Read on every recomposition (the screens tick every 30s), so labels
    // follow a timezone change the same way the day sections do.
    val zoneId = TimeZone.getDefault().id
    return remember(use24Hour, zoneId) {
        MatchTimeFormatter(timeZone = TimeZone.getTimeZone(zoneId), use24Hour = use24Hour)
    }
}

/** Wall clock that ticks every 30s, for countdowns and status labels. */
@Composable
internal fun rememberNow() = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(30_000)
        value = System.currentTimeMillis()
    }
}
