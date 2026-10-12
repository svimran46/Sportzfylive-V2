package com.sportzfy.live.presentation.settings

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sportzfy.live.core.theme.SportzfyTheme
import com.sportzfy.live.domain.model.ClockFormat
import com.sportzfy.live.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the stateless Settings UI with plain callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var language: String? = "unset"
    private var clockFormat: ClockFormat? = null
    private var clearCalls = 0

    // Nothing on Settings animates forever, so the clock runs normally;
    // dialogs open in their own window and need real frames to appear.
    private fun settle() = compose.waitForIdle()

    private fun show(savedCount: Int = 0, settings: UserSettings = UserSettings()) {
        compose.setContent {
            SportzfyTheme {
                SettingsContent(
                    state = SettingsUiState(settings = settings, savedCount = savedCount),
                    versionName = "1.2.0",
                    versionCode = 3,
                    onBack = {},
                    onSetLanguage = { language = it },
                    onSetClockFormat = { clockFormat = it },
                    onClearSaved = { clearCalls++ },
                )
            }
        }
        settle()
    }

    @Test
    fun showsTitleAndVersion() {
        show()
        compose.onNodeWithTag("screen-title").assertTextContains("Settings")
        compose.onNodeWithText("1.2.0 (3)").assertExists()
    }

    @Test
    fun timeFormatSegmentSetsClockFormat() {
        show()
        compose.onNode(hasText("24-hour") and isSelectable()).performClick()
        settle()
        assertEquals(ClockFormat.H24, clockFormat)
    }

    @Test
    fun clearWithNothingSavedDoesNothing() {
        show(savedCount = 0)
        compose.onNodeWithText("No saved matches").assertExists()
        compose.onNodeWithText("Clear saved matches").performClick()
        settle()
        compose.onNodeWithText("Clear saved matches?").assertDoesNotExist()
        assertEquals(0, clearCalls)
    }

    @Test
    fun clearAsksForConfirmation() {
        show(savedCount = 2)
        compose.onNodeWithText("2 saved").assertExists()
        compose.onNodeWithText("Clear saved matches").performClick()
        settle()
        compose.onNodeWithText("Clear saved matches?").assertExists()
        compose.onNodeWithText("This removes all 2 saved matches from this device.").assertExists()
        assertEquals(0, clearCalls)

        compose.onNodeWithText("Clear").performClick()
        settle()
        assertEquals(1, clearCalls)
        compose.onNodeWithText("Clear saved matches?").assertDoesNotExist()
    }

    @Test
    fun languageDialogPicksALanguage() {
        show()
        compose.onNodeWithText("Preferred stream language").performClick()
        settle()
        compose.onNodeWithText("Stream language").assertExists()

        compose.onNodeWithText("Spanish").performClick()
        settle()
        assertEquals("Spanish", language)
        compose.onNodeWithText("Stream language").assertDoesNotExist()
    }

    @Test
    fun languageDialogOffersAnyLanguage() {
        show()
        compose.onNodeWithText("Preferred stream language").performClick()
        settle()
        compose.onNodeWithText("Any language").performClick()
        settle()
        assertNull(language)
    }
}
