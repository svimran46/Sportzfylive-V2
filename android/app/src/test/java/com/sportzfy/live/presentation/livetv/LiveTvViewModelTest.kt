package com.sportzfy.live.presentation.livetv

import com.sportzfy.live.domain.model.ChannelCategory.CRICKET
import com.sportzfy.live.domain.model.ChannelCategory.FOOTBALL
import com.sportzfy.live.domain.model.ChannelCategory.OTHERS
import com.sportzfy.live.domain.model.ChannelFeed
import com.sportzfy.live.domain.model.ChannelLibrary
import com.sportzfy.live.presentation.model.ChannelShelf
import com.sportzfy.live.presentation.model.ChannelSort
import com.sportzfy.live.presentation.model.testChannel
import com.sportzfy.live.testing.FakeChannelLibraryRepository
import com.sportzfy.live.testing.FakeChannelRepository
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
class LiveTvViewModelTest {

    private val channels = listOf(
        testChannel("Sky Sports Football", FOOTBALL, "United Kingdom"),
        testChannel("Sky Sports Cricket", CRICKET, "United Kingdom"),
        testChannel("Willow Cricket", CRICKET, "USA"),
        testChannel("ESPN", OTHERS, "USA"),
    )
    private val repository = FakeChannelRepository(channels)
    private val library = FakeChannelLibraryRepository(ChannelLibrary(recentIds = listOf("espn")))
    private lateinit var viewModel: LiveTvViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LiveTvViewModel(repository, library)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.collect() =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

    private val names get() = viewModel.state.value.channels.map { it.name }

    @Test
    fun `category and search narrow the directory`() = runTest {
        collect()
        assertEquals(4, names.size)

        viewModel.selectCategory(CRICKET)
        assertEquals(listOf("Sky Sports Cricket", "Willow Cricket"), names)

        viewModel.setQuery("willow")
        assertEquals(listOf("Willow Cricket"), names)

        viewModel.selectCategory(null)
        viewModel.setQuery("")
        assertEquals(4, names.size)
    }

    @Test
    fun `country, sort and shelf combine, and a sport chip leaves the shelf`() = runTest {
        collect()
        viewModel.selectCountry("USA")
        viewModel.setSort(ChannelSort.A_Z)
        assertEquals(listOf("ESPN", "Willow Cricket"), names)

        viewModel.selectShelf(ChannelShelf.RECENT)
        assertEquals(ChannelShelf.RECENT, viewModel.state.value.selectedShelf)
        assertEquals(listOf("ESPN"), names)

        viewModel.selectCategory(CRICKET)
        assertEquals(null, viewModel.state.value.selectedShelf)
        assertEquals(listOf("Willow Cricket"), names)
    }

    @Test
    fun `stars are saved and shown`() = runTest {
        collect()
        viewModel.toggleFavorite("willow-cricket")
        assertEquals(listOf("willow-cricket"), library.value.favoriteIds)
        assertTrue("willow-cricket" in viewModel.state.value.favoriteIds)
        assertTrue(ChannelShelf.FAVORITES in viewModel.state.value.shelves)
    }

    @Test
    fun `directory updates flow through`() = runTest {
        collect()
        repository.emit(ChannelFeed(channels.take(1), updatedAtMillis = 2L, isLoading = false, error = null))
        assertEquals(listOf("Sky Sports Football"), names)
    }

    @Test
    fun `a forced refresh is dropped while one is running`() = runTest {
        viewModel.refresh(force = true)
        assertEquals(1, repository.forcedRefreshCalls)

        repository.emit(ChannelFeed(channels, updatedAtMillis = 1L, isLoading = true, error = null))
        viewModel.refresh(force = true)
        assertEquals(1, repository.forcedRefreshCalls)
        assertTrue(viewModel.state.value.channels.isNotEmpty())
    }
}
