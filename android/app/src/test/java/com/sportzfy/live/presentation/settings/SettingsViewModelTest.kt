package com.sportzfy.live.presentation.settings

import com.sportzfy.live.domain.model.ClockFormat
import com.sportzfy.live.domain.model.UserSettings
import com.sportzfy.live.testing.FakeFavoritesRepository
import com.sportzfy.live.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val settings = FakeSettingsRepository(UserSettings(preferredLanguage = "English", clockFormat = ClockFormat.SYSTEM))
    private val favorites = FakeFavoritesRepository(setOf("a", "b"))
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(settings, favorites)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collect() =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

    private val state get() = viewModel.state.value

    @Test
    fun `state reflects settings and saved count`() = runTest {
        collect()
        assertEquals("English", state.settings.preferredLanguage)
        assertEquals(ClockFormat.SYSTEM, state.settings.clockFormat)
        assertEquals(2, state.savedCount)

        settings.emit(UserSettings(preferredLanguage = "Arabic", clockFormat = ClockFormat.H12))
        assertEquals("Arabic", state.settings.preferredLanguage)
        assertEquals(ClockFormat.H12, state.settings.clockFormat)
    }

    @Test
    fun `preferred language persists, including any language`() = runTest {
        collect()
        viewModel.setPreferredLanguage(null)
        assertNull(settings.current.preferredLanguage)
        assertNull(state.settings.preferredLanguage)

        viewModel.setPreferredLanguage("Spanish")
        assertEquals("Spanish", settings.current.preferredLanguage)
        assertEquals("Spanish", state.settings.preferredLanguage)
    }

    @Test
    fun `clock format persists`() = runTest {
        collect()
        viewModel.setClockFormat(ClockFormat.H24)
        assertEquals(ClockFormat.H24, settings.current.clockFormat)
        assertEquals(ClockFormat.H24, state.settings.clockFormat)
    }

    @Test
    fun `clearing saved matches empties favorites`() = runTest {
        collect()
        assertEquals(2, state.savedCount)
        viewModel.clearSavedMatches()
        assertEquals(0, state.savedCount)
    }
}
