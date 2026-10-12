package com.sportzfy.live.presentation.catalog

import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.presentation.model.CatalogUiState
import com.sportzfy.live.presentation.model.testMatch
import com.sportzfy.live.domain.model.Announcement
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.testing.FakeAppConfigRepository
import com.sportzfy.live.testing.FakeFavoritesRepository
import com.sportzfy.live.testing.FakeMatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {

    private val matches = listOf(
        testMatch("madrid", LIVE, SportCategory.FOOTBALL, home = "Real Madrid", away = "Manchester City", popular = true),
        testMatch("arsenal", UPCOMING, SportCategory.FOOTBALL, home = "Arsenal", away = "Chelsea", start = 10),
        testMatch("india", LIVE, SportCategory.CRICKET, home = "India", away = "Australia"),
    )
    private val matchRepository = FakeMatchRepository(matches)
    private val favorites = FakeFavoritesRepository()
    private val config = FakeAppConfigRepository()
    private lateinit var viewModel: CatalogViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CatalogViewModel(matchRepository, favorites, config)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun CatalogUiState.ids(): List<String> =
        listOfNotNull(hero?.id) + sections.flatMap { it.matches }.map { it.id }

    /** stateIn(WhileSubscribed) only updates while something collects it. */
    private fun kotlinx.coroutines.test.TestScope.collectState() =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    @Test
    fun `selecting a sport filters the list`() = runTest {
        collectState()
        assertEquals(setOf("madrid", "arsenal", "india"), viewModel.uiState.value.ids().toSet())

        viewModel.selectCategory(SportCategory.CRICKET)

        assertEquals(listOf("india"), viewModel.uiState.value.ids())
        assertEquals(SportCategory.CRICKET, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun `search narrows the list`() = runTest {
        collectState()
        viewModel.setQuery("chelsea")
        assertEquals(listOf("arsenal"), viewModel.uiState.value.ids())
    }

    @Test
    fun `saving a match and opening Saved shows only saved matches`() = runTest {
        collectState()
        viewModel.toggleFavorite("india")
        viewModel.showFavorites()

        val state = viewModel.uiState.value
        assertTrue(state.favoritesOnly)
        assertEquals(listOf("india"), state.ids())
        assertEquals(1, state.savedCount)

        viewModel.selectCategory(null)
        assertEquals(false, viewModel.uiState.value.favoritesOnly)
    }

    @Test
    fun `feed updates flow through`() = runTest {
        collectState()
        matchRepository.emit(MatchFeed(matches.take(1), updatedAtMillis = 2L, isLoading = false, error = null))
        assertEquals(listOf("madrid"), viewModel.uiState.value.ids())
    }

    @Test
    fun `remote config changes apply live`() = runTest {
        collectState()
        config.emit(
            AppConfig(
                hiddenCategories = setOf(SportCategory.CRICKET),
                featuredMatchId = "arsenal",
                announcement = Announcement("Big match tonight", null),
            )
        )

        val state = viewModel.uiState.value
        assertEquals("arsenal", state.hero?.id)
        assertEquals(setOf("madrid", "arsenal"), state.ids().toSet())
        assertEquals("Big match tonight", state.announcement?.message)
    }

    @Test
    fun `refresh delegates to the repository`() = runTest {
        viewModel.refresh()
        viewModel.refresh(force = true)
        assertEquals(2, matchRepository.refreshCalls)
        assertEquals(1, matchRepository.forcedRefreshCalls)
    }
}
