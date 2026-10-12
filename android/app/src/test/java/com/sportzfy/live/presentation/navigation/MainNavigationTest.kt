package com.sportzfy.live.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import com.sportzfy.live.core.theme.SportzfyTheme
import com.sportzfy.live.core.time.ZoneProvider
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.presentation.catalog.CatalogViewModel
import com.sportzfy.live.presentation.model.testMatch
import com.sportzfy.live.testing.FakeAppConfigRepository
import com.sportzfy.live.testing.FakeClock
import com.sportzfy.live.testing.FakeFavoritesRepository
import com.sportzfy.live.testing.FakeMatchRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/** Drives the real app shell: bottom bar, tab routes and back navigation. */
// Tall screen: the lazy list only composes what fits on it.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class MainNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val hour = 3_600_000L

    /** Monday 2026-10-12 12:00 UTC. */
    private val noon = 1_791_806_400_000L

    @Before
    fun setUp() {
        val matches = listOf(
            testMatch("today-live", LIVE, title = "Live Derby"),
            testMatch("tomorrow", UPCOMING, start = noon + 20 * hour, title = "Tomorrow Classic"),
        )
        startKoin {
            modules(
                module {
                    viewModel {
                        CatalogViewModel(
                            FakeMatchRepository(matches),
                            FakeFavoritesRepository(),
                            FakeAppConfigRepository(),
                            FakeClock(noon),
                            ZoneProvider { TimeZone.getTimeZone("UTC") },
                        )
                    }
                }
            )
        }
    }

    @After
    fun tearDown() = stopKoin()

    // Infinite animations (live dot, spinners): advance the clock by hand.
    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(hasText(label) and isSelectable())

    @Test
    fun bottomBarSwitchesTabsAndBackReturnsToToday() {
        compose.mainClock.autoAdvance = false
        compose.setContent { SportzfyTheme { SportzfyNavHost() } }
        settle()

        // Launch: Today is selected and shows today's match only.
        tab("Today").assertIsSelected()
        compose.onAllNodesWithText("Live Derby", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("Tomorrow Classic", substring = true).assertCountEquals(0)

        tab("Tomorrow").performClick()
        settle()
        tab("Tomorrow").assertIsSelected()
        tab("Today").assertIsNotSelected()
        compose.onNodeWithTag("screen-title").assertTextContains("Tomorrow")
        compose.onAllNodesWithText("Tomorrow Classic", substring = true).assertCountEquals(1)

        tab("Live TV").performClick()
        settle()
        tab("Live TV").assertIsSelected()
        compose.onNodeWithText("Live TV is coming").assertExists()

        tab("All Matches").performClick()
        settle()
        compose.onNodeWithTag("screen-title").assertTextContains("All Matches")
        compose.onAllNodesWithText("Tomorrow Classic", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("Live Derby", substring = true).assertCountEquals(1)

        // Back from any tab goes to Today.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        tab("Today").assertIsSelected()
    }
}
