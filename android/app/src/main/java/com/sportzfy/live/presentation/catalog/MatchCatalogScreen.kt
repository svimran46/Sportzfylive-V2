package com.sportzfy.live.presentation.catalog

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
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
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.presentation.components.CategoryPills
import com.sportzfy.live.presentation.components.HeroSpotlight
import com.sportzfy.live.presentation.components.AnnouncementBanner
import com.sportzfy.live.presentation.components.InlineBanner
import com.sportzfy.live.presentation.components.LoadingState
import com.sportzfy.live.presentation.components.MatchCard
import com.sportzfy.live.presentation.components.MessageState
import com.sportzfy.live.presentation.components.PulsingDot
import com.sportzfy.live.presentation.components.tvFocus
import com.sportzfy.live.presentation.model.CatalogUiState
import com.sportzfy.live.presentation.model.MatchTimeFormatter
import com.sportzfy.live.presentation.model.userMessage
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

private const val AUTO_REFRESH_MILLIS = 60_000L

@Composable
fun MatchCatalogScreen(
    onOpenMatch: (String) -> Unit,
    viewModel: CatalogViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
) {
    var searchOpen by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    val formatter = rememberMatchTimeFormatter()
    val now by rememberNow()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CatalogHeader(
                liveCount = state.liveCount,
                searchOpen = searchOpen,
                query = state.query,
                onQueryChange = onQueryChange,
                onOpenSearch = { searchOpen = true },
                onCloseSearch = {
                    searchOpen = false
                    onQueryChange("")
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && state.hasData,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                state.isInitialLoading -> LoadingState()
                !state.hasData && state.error != null -> MessageState(
                    message = state.error.userMessage(),
                    actionLabel = "Retry",
                    onAction = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    catalogItems(state, now, formatter, onOpenMatch, onSelectCategory, onSelectSaved, onToggleFavorite)
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
) {
    val fullSpan: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }

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
        )
    }
    if (state.isEmpty) {
        item(key = "empty", span = fullSpan) {
            MessageState(
                when {
                    state.query.isNotBlank() -> "No matches for \"${state.query.trim()}\"."
                    state.favoritesOnly -> "No saved matches today. Tap the star on a match to save it."
                    else -> "No fixtures in this category right now."
                }
            )
        }
    }
    state.sections.forEach { section ->
        item(key = "section-${section.kind}", span = fullSpan) {
            SectionHeader(section.kind.title, section.matches.size)
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
    liveCount: Int,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
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
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Text(
                text = buildAnnotatedString {
                    append("SPORTZ")
                    withStyle(SpanStyle(color = SportzfyColors.Rose)) { append("FY") }
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            )
            Spacer(Modifier.width(12.dp))
            LiveCounter(liveCount)
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
    val use24Hour = DateFormat.is24HourFormat(context)
    return remember(use24Hour) { MatchTimeFormatter(use24Hour = use24Hour) }
}

/** Wall clock that ticks every 30s, for countdowns and status labels. */
@Composable
internal fun rememberNow() = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(30_000)
        value = System.currentTimeMillis()
    }
}
