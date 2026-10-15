package com.sportzfy.live.presentation.livetv

import com.sportzfy.live.domain.model.ChannelCategory.CRICKET
import com.sportzfy.live.domain.model.ChannelCategory.FOOTBALL
import com.sportzfy.live.domain.model.ChannelCategory.OTHERS
import com.sportzfy.live.domain.model.ChannelFeed
import com.sportzfy.live.presentation.model.testChannel
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
        testChannel("Sky Sports Football", FOOTBALL),
        testChannel("Sky Sports Cricket", CRICKET),
        testChannel("Willow Cricket", CRICKET),
        testChannel("ESPN", OTHERS),
    )
    private val repository = FakeChannelRepository(channels)
    private lateinit var viewModel: LiveTvViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LiveTvViewModel(repository)
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
