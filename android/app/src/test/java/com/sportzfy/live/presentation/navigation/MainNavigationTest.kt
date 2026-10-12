package com.sportzfy.live.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.sportzfy.live.domain.model.ChannelCategory
import com.sportzfy.live.presentation.catalog.CatalogViewModel
import com.sportzfy.live.domain.repository.SettingsRepository
import com.sportzfy.live.presentation.livetv.LiveTvViewModel
import com.sportzfy.live.presentation.settings.SettingsViewModel
import com.sportzfy.live.presentation.model.testChannel
import com.sportzfy.live.presentation.model.testMatch
import com.sportzfy.live.testing.FakeAppConfigRepository
import com.sportzfy.live.testing.FakeChannelRepository
import com.sportzfy.live.testing.FakeClock
import com.sportzfy.live.testing.FakeFavoritesRepository
import com.sportzfy.live.testing.FakeMatchRepository
import com.sportzfy.live.testing.FakeSettingsRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
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
        // Started once per test JVM and never stopped: Koin's Compose helpers keep
        // a reference to the first root scope, so a stopped-and-restarted Koin
        // fails the next test with "Scope '_root_' is closed". The definitions
        // are identical for every test and build fresh fakes per ViewModel.
        if (GlobalContext.getOrNull() != null) return
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
                    single<SettingsRepository> { FakeSettingsRepository() }
                    viewModel { SettingsViewModel(get(), FakeFavoritesRepository()) }
                    viewModel { LiveTvViewModel(FakeChannelRepository(listOf(testChannel("Willow Cricket", ChannelCategory.CRICKET)))) }
                }
            )
        }
    }


    // Infinite animations (live dot, spinners): advance the clock by hand.
    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    // Bottom tabs and drawer rows share labels ("Live TV"); their roles tell them apart.
    private fun tab(label: String): SemanticsNodeInteraction =
        compose.onNode(hasText(label) and isSelectable() and hasRole(Role.Tab))

    private fun drawerItem(label: String): SemanticsNodeInteraction =
        compose.onNode(hasText(label) and isSelectable() and hasRole(Role.Button))

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun openDrawer() {
        compose.onNodeWithContentDescription("Open menu").performClick()
        settle()
    }

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
        compose.onNodeWithTag("screen-title").assertTextContains("Live TV")
        compose.onNodeWithText("Willow Cricket").assertExists()

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

    @Test
    fun drawerOpensClosesAndNavigates() {
        compose.mainClock.autoAdvance = false
        compose.setContent { SportzfyTheme { SportzfyNavHost() } }
        settle()

        openDrawer()
        compose.onNodeWithContentDescription("Close menu").assertIsDisplayed()
        drawerItem("Live Matches").assertIsSelected()

        // A sport entry lands on All Matches with that sport filtered.
        drawerItem("Cricket").performClick()
        settle()
        compose.onNodeWithContentDescription("Close menu").assertIsNotDisplayed()
        tab("All Matches").assertIsSelected()
        compose.onNodeWithTag("screen-title").assertTextContains("All Matches")
        compose.onAllNodesWithText("Live Derby", substring = true).assertCountEquals(0)

        openDrawer()
        drawerItem("Cricket").assertIsSelected()
        drawerItem("Live Matches").assertIsNotSelected()

        // Close button.
        compose.onNodeWithContentDescription("Close menu").performClick()
        settle()
        compose.onNodeWithContentDescription("Close menu").assertIsNotDisplayed()

        // Home resets the filter and opens Live Matches.
        openDrawer()
        drawerItem("Live Matches").performClick()
        settle()
        tab("Today").assertIsSelected()
        compose.onAllNodesWithText("Live Derby", substring = true).assertCountEquals(1)

        // Footer entries open full screens.
        openDrawer()
        drawerItem("Settings").performClick()
        settle()
        compose.onNodeWithTag("screen-title").assertTextContains("Settings")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        tab("Today").assertIsSelected()
    }
}
