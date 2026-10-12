package com.sportzfy.live.presentation.catalog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sportzfy.live.core.theme.SportzfyTheme
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.presentation.model.testMatch
import com.sportzfy.live.testing.FakeFavoritesRepository
import com.sportzfy.live.testing.FakeMatchRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the real catalog screen and ViewModel on Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class CatalogScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // The live dot and loading spinner animate forever, so the clock is
    // advanced by hand instead of waiting for Compose to go idle on its own.
    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private val viewModel = CatalogViewModel(
        FakeMatchRepository(
            listOf(
                testMatch("madrid", LIVE, SportCategory.FOOTBALL, home = "Real Madrid", away = "Manchester City"),
                testMatch("arsenal", LIVE, SportCategory.FOOTBALL, home = "Arsenal", away = "Chelsea"),
                testMatch("india", LIVE, SportCategory.CRICKET, home = "India", away = "Australia"),
            )
        ),
        FakeFavoritesRepository(),
    )

    @Test
    fun categoryPillFiltersTheMatchList() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SportzfyTheme { MatchCatalogScreen(onOpenMatch = {}, viewModel = viewModel) }
        }
        settle()

        compose.onAllNodesWithText("Arsenal", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("India", substring = true).assertCountEquals(1)

        compose.onNodeWithText("Cricket  1").performClick()
        settle()

        compose.onAllNodesWithText("Arsenal", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Real Madrid", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("India", substring = true).assertCountEquals(1)
    }

    @Test
    fun searchFiltersTheMatchList() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SportzfyTheme { MatchCatalogScreen(onOpenMatch = {}, viewModel = viewModel) }
        }
        settle()

        viewModel.setQuery("chelsea")
        settle()

        compose.onAllNodesWithText("Arsenal", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("India", substring = true).assertCountEquals(0)
    }
}
