package com.sportzfy.live.presentation.catalog

import com.sportzfy.live.domain.model.MatchFeed
import com.sportzfy.live.domain.model.MatchStatus.LIVE
import com.sportzfy.live.domain.model.MatchStatus.UPCOMING
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.core.time.ZoneProvider
import com.sportzfy.live.presentation.model.CatalogUiState
import com.sportzfy.live.presentation.model.DayScope
import com.sportzfy.live.presentation.model.HomeView
import com.sportzfy.live.presentation.model.testMatch
import com.sportzfy.live.domain.model.Announcement
import com.sportzfy.live.domain.model.AppConfig
import com.sportzfy.live.testing.FakeAppConfigRepository
import com.sportzfy.live.testing.FakeClock
import com.sportzfy.live.testing.FakeFavoritesRepository
import com.sportzfy.live.testing.FakeMatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {

    private val hour = 3_600_000L

    /** Monday 2026-10-12 12:00 UTC. */
    private val noon = 1_791_806_400_000L
    private val clock = FakeClock(noon)
    private val utc = ZoneProvider { TimeZone.getTimeZone("UTC") }

    private val matches = listOf(
        testMatch("madrid", LIVE, SportCategory.FOOTBALL, home = "Real Madrid", away = "Manchester City", popular = true),
        testMatch("arsenal", UPCOMING, SportCategory.FOOTBALL, start = noon + hour, home = "Arsenal", away = "Chelsea"),
        testMatch("india", LIVE, SportCategory.CRICKET, home = "India", away = "Australia"),
        testMatch("night", UPCOMING, SportCategory.TENNIS, start = noon + 12 * hour + 30 * 60_000L, title = "Night Final"),
    )
    private val matchRepository = FakeMatchRepository(matches)
    private val favorites = FakeFavoritesRepository()
    private val config = FakeAppConfigRepository()
    private lateinit var viewModel: CatalogViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CatalogViewModel(matchRepository, favorites, config, clock, utc)
        // These tests cover filters and days over all of today; Home's
        // Live | Upcoming | All switch has its own tests below.
        viewModel.setHomeView(HomeView.ALL)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun CatalogUiState.ids(): List<String> =
        listOfNotNull(hero?.id) + sections.flatMap { it.matches }.map { it.id }

    /** stateIn(WhileSubscribed) only updates while something collects it. */
    private fun kotlinx.coroutines.test.TestScope.collectState(scope: DayScope = DayScope.TODAY) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state(scope).collect {} }

    private val today get() = viewModel.state(DayScope.TODAY).value

    @Test
    fun `selecting a sport filters the list`() = runTest {
        collectState()
        assertEquals(setOf("madrid", "arsenal", "india"), today.ids().toSet())

        viewModel.selectCategory(SportCategory.CRICKET)

        assertEquals(listOf("india"), today.ids())
        assertEquals(SportCategory.CRICKET, today.selectedCategory)
    }

    @Test
    fun `search narrows the list`() = runTest {
        collectState()
        viewModel.setQuery("chelsea")
        assertEquals(listOf("arsenal"), today.ids())
    }

    @Test
    fun `saving a match and opening Saved shows only saved matches`() = runTest {
        collectState()
        viewModel.toggleFavorite("india")
        viewModel.showFavorites()

        val state = today
        assertTrue(state.favoritesOnly)
        assertEquals(listOf("india"), state.ids())
        assertEquals(1, state.savedCount)

        viewModel.selectCategory(null)
        assertEquals(false, today.favoritesOnly)
    }

    @Test
    fun `feed updates flow through`() = runTest {
        collectState()
        matchRepository.emit(MatchFeed(matches.take(1), updatedAtMillis = 2L, isLoading = false, error = null))
        assertEquals(listOf("madrid"), today.ids())
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

        val state = today
        assertEquals("arsenal", state.hero?.id)
        assertEquals(setOf("madrid", "arsenal"), state.ids().toSet())
        assertEquals("Big match tonight", state.announcement?.message)
    }

    @Test
    fun `tabs slice the same feed by day`() = runTest {
        collectState(DayScope.TODAY)
        collectState(DayScope.TOMORROW)
        collectState(DayScope.ALL)

        assertEquals(setOf("madrid", "arsenal", "india"), today.ids().toSet())
        assertEquals(listOf("night"), viewModel.state(DayScope.TOMORROW).value.ids())
        assertEquals(4, viewModel.state(DayScope.ALL).value.ids().size)
    }

    @Test
    fun `filters and search apply to every tab`() = runTest {
        collectState(DayScope.TOMORROW)
        viewModel.setQuery("final")
        assertEquals(listOf("night"), viewModel.state(DayScope.TOMORROW).value.ids())
        viewModel.selectCategory(SportCategory.FOOTBALL)
        assertTrue(viewModel.state(DayScope.TOMORROW).value.isEmpty)
    }

    @Test
    fun `tomorrow becomes today after midnight`() = runTest {
        clock.now = noon + 11 * hour + 59 * 60_000L // 23:59
        collectState(DayScope.TODAY)
        collectState(DayScope.TOMORROW)
        assertEquals(listOf("night"), viewModel.state(DayScope.TOMORROW).value.ids())

        clock.now = noon + 12 * hour + 60_000L // 00:01 the next day
        advanceTimeBy(61_000)

        assertTrue(viewModel.state(DayScope.TOMORROW).value.isEmpty)
        assertTrue("night" in today.ids())
    }

    @Test
    fun `home opens on source-confirmed live matches only`() = runTest {
        viewModel = CatalogViewModel(matchRepository, favorites, config, clock, utc)
        collectState()

        assertEquals(HomeView.LIVE, today.homeView)
        assertEquals(setOf("madrid", "india"), today.ids().toSet())
        assertEquals(2, today.listCount)
    }

    @Test
    fun `home switch slices today and leaves other tabs alone`() = runTest {
        collectState(DayScope.TODAY)
        collectState(DayScope.TOMORROW)

        viewModel.setHomeView(HomeView.UPCOMING)
        assertEquals(listOf("arsenal"), today.ids())
        assertEquals(listOf("night"), viewModel.state(DayScope.TOMORROW).value.ids())
        assertEquals(null, viewModel.state(DayScope.TOMORROW).value.homeView)

        viewModel.setHomeView(HomeView.LIVE)
        assertEquals(setOf("madrid", "india"), today.ids().toSet())
    }

    @Test
    fun `a forced refresh is dropped while one is running`() = runTest {
        matchRepository.emit(MatchFeed(matches, updatedAtMillis = 1L, isLoading = true, error = null))
        viewModel.refresh(force = true)
        assertEquals(0, matchRepository.forcedRefreshCalls)
    }

    @Test
    fun `drawer filters replace each other and reset clears them`() = runTest {
        collectState(DayScope.TODAY)
        collectState(DayScope.ALL)
        val all = { viewModel.state(DayScope.ALL).value }

        viewModel.showOtherSports()
        assertEquals(setOf("night"), all().ids().toSet())
        assertTrue(all().otherSports)

        viewModel.selectCategory(SportCategory.CRICKET)
        assertEquals(false, all().otherSports)
        assertEquals(listOf("india"), all().ids())

        viewModel.openSearch()
        viewModel.setQuery("india")
        assertTrue(all().searchActive)

        viewModel.resetFilters()
        assertEquals(4, all().ids().size)
        assertEquals(false, all().searchActive)
        assertEquals("", all().query)
        assertEquals(HomeView.ALL, today.homeView)
    }

    @Test
    fun `closing search clears the query`() = runTest {
        collectState()
        viewModel.openSearch()
        viewModel.setQuery("chelsea")
        viewModel.closeSearch()
        assertEquals(false, today.searchActive)
        assertEquals("", today.query)
    }

    @Test
    fun `refresh delegates to the repository`() = runTest {
        viewModel.refresh()
        viewModel.refresh(force = true)
        assertEquals(2, matchRepository.refreshCalls)
        assertEquals(1, matchRepository.forcedRefreshCalls)
    }
}
