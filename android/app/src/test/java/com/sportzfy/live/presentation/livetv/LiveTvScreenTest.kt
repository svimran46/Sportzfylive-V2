package com.sportzfy.live.presentation.livetv

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    private val viewModel = LiveTvViewModel(
        FakeChannelRepository(
            listOf(
                testChannel("Sky Sports Football", FOOTBALL),
                testChannel("Sky Sports Cricket", CRICKET),
                testChannel("Willow Cricket", CRICKET),
                testChannel("ESPN", OTHERS),
            )
        )
    )

    private fun show(onOpen: (String) -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SportzfyTheme { LiveTvScreen(onOpenChannel = onOpen, viewModel = viewModel) } }
        settle()
    }

    @Test
    fun categoryChipFiltersChannels() {
        show()
        compose.onNodeWithTag("screen-title").assertTextContains("Live TV")
        compose.onAllNodesWithText("ESPN").assertCountEquals(1)

        compose.onNodeWithText("Cricket  2").performClick()
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
    fun tappingAChannelOpensIt() {
        val opened = mutableListOf<String>()
        show { opened += it }
        compose.onNodeWithText("Willow Cricket").performClick()
        assertEquals(listOf("willow-cricket"), opened)
    }
}
