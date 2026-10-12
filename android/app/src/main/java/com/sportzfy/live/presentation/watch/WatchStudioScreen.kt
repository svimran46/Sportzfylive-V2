package com.sportzfy.live.presentation.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportzfy.live.core.player.PlayerConfig
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.Team
import com.sportzfy.live.presentation.catalog.rememberMatchTimeFormatter
import com.sportzfy.live.presentation.catalog.rememberNow
import com.sportzfy.live.presentation.config.LocalAppConfig
import com.sportzfy.live.presentation.components.FavoriteButton
import com.sportzfy.live.presentation.components.MatchCard
import com.sportzfy.live.presentation.components.MatchRailCard
import com.sportzfy.live.presentation.components.EmptyState
import com.sportzfy.live.presentation.components.StatusTag
import com.sportzfy.live.presentation.components.StreamChip
import com.sportzfy.live.presentation.components.TeamCrest
import com.sportzfy.live.presentation.components.tvFocus
import com.sportzfy.live.presentation.model.MatchTimeFormatter
import com.sportzfy.live.presentation.watch.player.StreamPlayer
import com.sportzfy.live.presentation.watch.player.StreamPlayerState
import com.sportzfy.live.presentation.watch.player.rememberStreamPlayerState
import org.koin.compose.viewmodel.koinViewModel

private val WideLayoutMinWidth = 840.dp

@Composable
fun WatchStudioScreen(
    onBack: () -> Unit,
    viewModel: WatchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WatchStudioContent(
        state = state,
        onBack = onBack,
        onSelectStream = viewModel::selectStream,
        onSelectMatch = viewModel::selectMatch,
        onRetryStreams = viewModel::retryStreams,
        onToggleFavorite = viewModel::toggleFavorite,
    )
}

@Composable
fun WatchStudioContent(
    state: WatchUiState,
    onBack: () -> Unit,
    onSelectStream: (String) -> Unit,
    onSelectMatch: (String) -> Unit,
    onRetryStreams: () -> Unit,
    onToggleFavorite: () -> Unit,
    playerState: StreamPlayerState = rememberStreamPlayerState(),
) {
    val formatter = rememberMatchTimeFormatter()
    val now by rememberNow()

    // One player instance survives switching between the phone and wide
    // layouts (e.g. on rotation), so the stream does not restart.
    val player = remember(playerState) {
        movableContentOf { url: String?, modifier: Modifier ->
            StreamPlayer(
                url = url,
                modifier = modifier,
                state = playerState,
                config = PlayerConfig.from(LocalAppConfig.current),
            )
        }
    }
    val playerUrl = state.selectedStream?.embedUrl

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WatchTopBar(state.match, state.isFavorite, onBack, onToggleFavorite) },
    ) { padding ->
        if (state.isMatchMissing) {
            // Offline or rate-limited: offer Retry instead of claiming the match is gone.
            if (state.isLoadFailed) {
                EmptyState(
                    icon = SportzfyIcons.Offline,
                    title = "Couldn't load this match",
                    message = "Couldn't load this match. Check your connection and try again.",
                    primaryAction = "Retry" to onRetryStreams,
                    modifier = Modifier.padding(padding).fillMaxSize().wrapContentSize(),
                )
            } else {
                EmptyState(
                    icon = SportzfyIcons.Alert,
                    title = "Match unavailable",
                    message = "This match is no longer available.",
                    primaryAction = "Back to matches" to onBack,
                    modifier = Modifier.padding(padding).fillMaxSize().wrapContentSize(),
                )
            }
            return@Scaffold
        }

        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val details: @Composable () -> Unit = {
                MatchDetails(state, now, formatter)
                StreamSelector(state, playerState, onSelectStream, onRetryStreams)
            }

            // Landscape phones (e.g. 640x360dp) also go side by side: a pinned
            // full-width 16:9 player would leave no room for the stream picker.
            if (maxWidth >= WideLayoutMinWidth || maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(0.68f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        player(playerUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        details()
                    }
                    RelatedColumn(
                        matches = state.related,
                        now = now,
                        formatter = formatter,
                        onSelectMatch = onSelectMatch,
                        modifier = Modifier.weight(0.32f).fillMaxSize(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    // The player stays pinned while the details scroll beneath it,
                    // capped so the picker below always stays reachable.
                    player(
                        playerUrl,
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .heightIn(max = maxHeight * 0.55f)
                            .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
                    )
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        details()
                        RelatedRail(state.related, now, formatter, onSelectMatch)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchTopBar(match: Match?, isFavorite: Boolean, onBack: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.tvFocus(CircleShape)) {
            SportzfyIcon(SportzfyIcons.Back, contentDescription = "Back")
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = match?.category?.displayName?.uppercase() ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = SportzfyColors.RoseText,
            )
            Text(
                text = match?.title ?: "Loading…",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (match != null) FavoriteButton(isFavorite, onToggleFavorite)
    }
}

@Composable
private fun MatchDetails(state: WatchUiState, now: Long, formatter: MatchTimeFormatter) {
    val match = state.match ?: return
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        val home = match.homeTeam
        val away = match.awayTeam
        if (home != null && away != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamColumn(home, Modifier.weight(1f))
                StatusTag(match, now, formatter, Modifier.padding(horizontal = 8.dp))
                TeamColumn(away, Modifier.weight(1f))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                StatusTag(match, now, formatter)
            }
        }
    }
}

@Composable
private fun TeamColumn(team: Team, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamCrest(team, size = 44.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = team.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StreamSelector(
    state: WatchUiState,
    playerState: StreamPlayerState,
    onSelectStream: (String) -> Unit,
    onRetryStreams: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Streams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (playerState.blockedCount > 0) ShieldNote(playerState.blockedCount)
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.isResolvingStreams -> Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = SportzfyColors.Rose)
                Spacer(Modifier.width(10.dp))
                Text("Finding streams…", style = MaterialTheme.typography.bodyMedium, color = SportzfyColors.TextSecondary)
            }
            state.streams.isEmpty() -> EmptyState(
                icon = if (state.failedSourceCount > 0) SportzfyIcons.Offline else SportzfyIcons.LiveTv,
                title = if (state.failedSourceCount > 0) "Sources not responding" else "No streams yet",
                message = if (state.failedSourceCount > 0) {
                    "Stream sources aren't responding right now."
                } else {
                    "No streams yet. They usually appear shortly before kick-off."
                },
                primaryAction = "Try again" to onRetryStreams,
            )
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.streams, key = { _, stream -> stream.key }) { index, stream ->
                        StreamChip(
                            index = index,
                            stream = stream,
                            selected = stream.key == state.selectedStreamKey,
                            onClick = { onSelectStream(stream.key) },
                        )
                    }
                }
                if (state.failedSourceCount > 0) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${state.failedSourceCount} more source(s) didn't respond.",
                            style = MaterialTheme.typography.labelMedium,
                            color = SportzfyColors.TextMuted,
                        )
                        TextButton(onClick = onRetryStreams, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShieldNote(blocked: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SportzfyIcon(SportzfyIcons.Shield, contentDescription = null, tint = SportzfyColors.Emerald, size = 14.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (blocked == 1) "1 pop-up blocked" else "$blocked pop-ups blocked",
            style = MaterialTheme.typography.labelMedium,
            color = SportzfyColors.Emerald,
        )
    }
}

@Composable
private fun RelatedRail(
    matches: List<Match>,
    now: Long,
    formatter: MatchTimeFormatter,
    onSelectMatch: (String) -> Unit,
) {
    if (matches.isEmpty()) return
    Text(
        text = "More live & upcoming",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(matches, key = { it.id }) { match ->
            MatchRailCard(match, now, formatter, onClick = { onSelectMatch(match.id) })
        }
    }
}

@Composable
private fun RelatedColumn(
    matches: List<Match>,
    now: Long,
    formatter: MatchTimeFormatter,
    onSelectMatch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("More live & upcoming", style = MaterialTheme.typography.titleMedium)
        if (matches.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing else on right now.", color = SportzfyColors.TextMuted)
            }
        }
        matches.forEach { match ->
            MatchCard(match, now, formatter, onClick = { onSelectMatch(match.id) }, compact = true)
        }
    }
}
