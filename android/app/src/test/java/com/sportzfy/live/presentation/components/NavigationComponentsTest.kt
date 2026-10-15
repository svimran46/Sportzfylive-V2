package com.sportzfy.live.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.core.theme.SportzfyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    private val items = listOf(
        NavBarItem("today", "Today", SportzfyIcons.Today),
        NavBarItem("tomorrow", "Tomorrow", SportzfyIcons.Tomorrow),
        NavBarItem("tv", "Live TV", SportzfyIcons.LiveTv),
        NavBarItem("all", "All Matches", SportzfyIcons.AllMatches),
    )

    @Test
    fun bottomBarSelectsTheTappedDestination() {
        val taps = mutableListOf<String>()
        compose.setContent {
            var selected by remember { mutableStateOf("today") }
            SportzfyTheme {
                BottomNavBar(items, selected, onSelect = { selected = it; taps += it })
            }
        }

        compose.onNodeWithText("Today").assertIsSelected()
        compose.onNodeWithText("Live TV").assertIsNotSelected()

        compose.onNodeWithText("Live TV").performClick()

        compose.onNodeWithText("Live TV").assertIsSelected()
        compose.onNodeWithText("Today").assertIsNotSelected()
        assertEquals(listOf("tv"), taps)
    }

    @Test
    fun segmentedControlSwitchesSelection() {
        compose.setContent {
            var index by remember { mutableIntStateOf(0) }
            SportzfyTheme { SegmentedControl(listOf("Live", "Upcoming", "All"), index, onSelect = { index = it }) }
        }

        compose.onNodeWithText("Live").assertIsSelected()
        compose.onNodeWithText("Upcoming").performClick()
        compose.onNodeWithText("Upcoming").assertIsSelected()
        compose.onNodeWithText("Live").assertIsNotSelected()
    }
}
