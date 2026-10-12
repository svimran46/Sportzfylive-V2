package com.sportzfy.live.presentation.livetv

import com.sportzfy.live.domain.model.ChannelCategory.CRICKET
import com.sportzfy.live.domain.model.ChannelCategory.FOOTBALL
import com.sportzfy.live.domain.model.ChannelFeed
import com.sportzfy.live.domain.model.DataError
import com.sportzfy.live.presentation.model.testChannel
import com.sportzfy.live.testing.FakeChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelPlayerViewModelTest {

    private val repository = FakeChannelRepository(
        listOf(
            testChannel("Sky Sports Cricket", CRICKET),
            testChannel("Willow Cricket", CRICKET),
            testChannel("Sky Sports Football", FOOTBALL),
        )
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opens the channel with others from its group`() {
        val viewModel = ChannelPlayerViewModel("sky-sports-cricket", repository)
        val state = viewModel.state.value
        assertEquals("Sky Sports Cricket", state.channel?.name)
        assertEquals("https://tv.example/sky-sports-cricket", state.channel?.streamUrl)
        assertEquals(listOf("Willow Cricket"), state.related.map { it.name })
    }

    @Test
    fun `switching channel stays on the player`() {
        val viewModel = ChannelPlayerViewModel("sky-sports-cricket", repository)
        viewModel.selectChannel("willow-cricket")
        assertEquals("Willow Cricket", viewModel.state.value.channel?.name)
        assertEquals(listOf("Sky Sports Cricket"), viewModel.state.value.related.map { it.name })
    }

    @Test
    fun `a failed directory load offers retry, which recovers`() {
        val channels = repository.feed.value.channels
        repository.emit(ChannelFeed(emptyList(), updatedAtMillis = null, isLoading = false, error = DataError.NETWORK))
        val viewModel = ChannelPlayerViewModel("willow-cricket", repository)
        assertTrue(viewModel.state.value.isLoadFailed)

        repository.emit(ChannelFeed(channels, updatedAtMillis = 2L, isLoading = false, error = null))
        viewModel.retry()
        assertEquals("Willow Cricket", viewModel.state.value.channel?.name)
        assertFalse(viewModel.state.value.isLoadFailed)
    }

    @Test
    fun `an unknown channel says so`() {
        val state = ChannelPlayerViewModel("gone", repository).state.value
        assertTrue(state.isMissing)
        assertNull(state.channel)
    }
}
