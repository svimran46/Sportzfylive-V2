package com.sportzfy.live.presentation.livetv

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import com.sportzfy.live.core.theme.SportzfyTheme
import com.sportzfy.live.domain.model.ChannelCategory.CRICKET
import com.sportzfy.live.domain.model.ChannelCategory.FOOTBALL
import com.sportzfy.live.domain.model.ChannelCategory.OTHERS
import com.sportzfy.live.presentation.model.testChannel
import com.sportzfy.live.testing.FakeChannelLibraryRepository
import com.sportzfy.live.testing.FakeChannelRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the real Live TV screen and ViewModel on Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class LiveTvScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private val library = FakeChannelLibraryRepository()

    private val viewModel = LiveTvViewModel(
        FakeChannelRepository(
            listOf(
                testChannel("Sky Sports Football", FOOTBALL, "United Kingdom"),
                testChannel("Sky Sports Cricket", CRICKET, "United Kingdom"),
                testChannel("Willow Cricket", CRICKET, "USA"),
                testChannel("ESPN", OTHERS, "USA"),
            )
        ),
        library,
    )

    private fun show(playing: String? = null, onPlay: (String, List<String>) -> Unit = { _, _ -> }) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SportzfyTheme { LiveTvScreen(onPlayChannel = onPlay, viewModel = viewModel, playingChannelId = playing) }
        }
        settle()
    }

    @Test
    fun categoryChipFiltersChannels() {
        show()
        compose.onNodeWithTag("screen-title").assertTextContains("Live TV")
        compose.onAllNodesWithText("ESPN").assertCountEquals(1)

        compose.onNodeWithText("Cricket").performClick()
        settle()

        compose.onAllNodesWithText("Sky Sports Cricket").assertCountEquals(1)
        compose.onAllNodesWithText("Willow Cricket").assertCountEquals(1)
        compose.onAllNodesWithText("ESPN").assertCountEquals(0)
        compose.onAllNodesWithText("Sky Sports Football").assertCountEquals(0)
    }

    @Test
    fun searchNarrowsAndClears() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        settle()
        compose.onAllNodesWithText("ESPN").assertCountEquals(0)
        compose.onNodeWithText("No channels match \"zzz\"").assertExists()
        compose.onNodeWithText("Clear search").performClick()
        settle()
        compose.onAllNodesWithText("ESPN").assertCountEquals(1)
    }

    @Test
    fun tappingAChannelPlaysItWithTheGridAsQueue() {
        val played = mutableListOf<Pair<String, List<String>>>()
        show { id, queue -> played += id to queue }
        compose.onNodeWithText("Willow Cricket").performClick()
        assertEquals(
            listOf("willow-cricket" to listOf("sky-sports-football", "sky-sports-cricket", "willow-cricket", "espn")),
            played,
        )
    }

    @Test
    fun theChannelPlayingIsMarkedLive() {
        show(playing = "espn")
        compose.onAllNodesWithText("LIVE").assertCountEquals(1)
    }

    @Test
    fun starringAChannelAddsTheFavoritesShelf() {
        show()
        compose.onAllNodesWithText("Favorites").assertCountEquals(0)
        compose.onNodeWithContentDescription("Add ESPN to favorites").performClick()
        settle()
        assertEquals(listOf("espn"), library.value.favoriteIds)
        compose.onNodeWithText("Favorites").performClick()
        settle()
        compose.onAllNodesWithText("ESPN").assertCountEquals(1)
        compose.onAllNodesWithText("Willow Cricket").assertCountEquals(0)
    }

    @Test
    fun countryPickerShowsTheSelectionAndAToZSorts() {
        show()
        compose.onNodeWithText("World").assertExists()
        compose.onNodeWithText("4 channels").assertExists()
        compose.onNodeWithText("A–Z").performClick()
        settle()
        assertEquals(
            listOf("ESPN", "Sky Sports Cricket", "Sky Sports Football", "Willow Cricket"),
            viewModel.state.value.channels.map { it.name },
        )
    }
}
