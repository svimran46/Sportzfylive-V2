package com.sportzfy.live.presentation.watch

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.sportzfy.live.core.theme.SportzfyTheme
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.presentation.model.testMatch
import com.sportzfy.live.testing.FakeFavoritesRepository
import com.sportzfy.live.testing.FakeMatchRepository
import com.sportzfy.live.testing.FakeStreamRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Renders the real watch screen (including the WebView player) on Robolectric. */
@RunWith(RobolectricTestRunner::class)
class StreamPickerTest {

    @get:Rule
    val compose = createComposeRule()

    // The live dot and loading spinner animate forever, so the clock is
    // advanced by hand instead of waiting for Compose to go idle on its own.
    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private val viewModel = WatchViewModel(
        initialMatchId = "madrid",
        matchRepository = FakeMatchRepository(listOf(testMatch("madrid", LIVE, home = "Real Madrid", away = "Manchester City"))),
        streamRepository = FakeStreamRepository(
            mapOf(
                StreamSource("alpha", "madrid") to listOf(
                    StreamItem("alpha", "madrid", 1, "English", isHd = true),
                    StreamItem("alpha", "madrid", 2, "Hindi", isHd = false),
                )
            )
        ),
        favoritesRepository = FakeFavoritesRepository(),
    )

    @Test
    fun pickingAStreamMakesItActive() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SportzfyTheme { WatchStudioScreen(onBack = {}, viewModel = viewModel) }
        }
        settle()

        compose.onNodeWithContentDescription("Stream 1 HD • English").assertIsSelected()
        compose.onNodeWithContentDescription("Stream 2 • Hindi").assertIsNotSelected()

        compose.onNodeWithContentDescription("Stream 2 • Hindi").performClick()
        settle()

        compose.onNodeWithContentDescription("Stream 2 • Hindi").assertIsSelected()
        compose.onNodeWithContentDescription("Stream 1 HD • English").assertIsNotSelected()
        assertEquals("https://embed.st/embed/alpha/madrid/2", viewModel.uiState.value.selectedStream?.embedUrl)
    }
}
