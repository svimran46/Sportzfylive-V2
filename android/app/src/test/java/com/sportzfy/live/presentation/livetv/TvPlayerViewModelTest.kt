package com.sportzfy.live.presentation.livetv

import com.sportzfy.live.domain.model.ChannelCategory.CRICKET
import com.sportzfy.live.domain.model.ChannelCategory.FOOTBALL
import com.sportzfy.live.presentation.livetv.player.TvPlayerViewModel
import com.sportzfy.live.presentation.livetv.player.TvPresentation
import com.sportzfy.live.presentation.model.testChannel
import com.sportzfy.live.testing.FakeChannelLibraryRepository
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
class TvPlayerViewModelTest {

    private val repository = FakeChannelRepository(
        listOf(
            testChannel("Sky Sports Cricket", CRICKET),
            testChannel("Willow Cricket", CRICKET),
            testChannel("Sky Sports Football", FOOTBALL),
        )
    )
    private val library = FakeChannelLibraryRepository()
    private lateinit var viewModel: TvPlayerViewModel

    private val queue = listOf("sky-sports-cricket", "willow-cricket", "sky-sports-football")
    private val playing get() = viewModel.state.value.channel?.id

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = TvPlayerViewModel(repository, library)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `playing opens docked and records the channel as watched`() {
        viewModel.play("willow-cricket", queue)
        assertEquals("willow-cricket", playing)
        assertEquals(TvPresentation.DOCKED, viewModel.state.value.presentation)
        assertEquals(listOf("willow-cricket"), library.value.recentIds)
    }

    @Test
    fun `next and previous walk the grid and wrap around`() {
        viewModel.play("sky-sports-football", queue)
        viewModel.next()
        assertEquals("sky-sports-cricket", playing)
        viewModel.previous()
        assertEquals("sky-sports-football", playing)
        viewModel.previous()
        assertEquals("willow-cricket", playing)
        assertEquals(listOf("willow-cricket", "sky-sports-football", "sky-sports-cricket"), library.value.recentIds)
    }

    @Test
    fun `a single channel has nothing to zap to`() {
        viewModel.play("willow-cricket")
        assertFalse(viewModel.state.value.canZap)
        viewModel.next()
        assertEquals("willow-cricket", playing)
    }

    @Test
    fun `zapping keeps the mini player and full screen`() {
        viewModel.play("sky-sports-cricket", queue)
        viewModel.minimize()
        viewModel.next()
        assertEquals(TvPresentation.MINI, viewModel.state.value.presentation)

        viewModel.enterFullscreen()
        viewModel.next()
        assertEquals(TvPresentation.FULLSCREEN, viewModel.state.value.presentation)
        viewModel.exitFullscreen()
        assertEquals(TvPresentation.DOCKED, viewModel.state.value.presentation)
    }

    @Test
    fun `the star follows the channel playing`() {
        viewModel.play("willow-cricket", queue)
        assertFalse(viewModel.state.value.isFavorite)
        viewModel.toggleFavorite()
        assertTrue(viewModel.state.value.isFavorite)
        assertEquals(listOf("willow-cricket"), library.value.favoriteIds)
        viewModel.next()
        assertFalse(viewModel.state.value.isFavorite)
    }

    @Test
    fun `close stops playback, and a new session opens docked again`() {
        viewModel.play("willow-cricket", queue)
        viewModel.minimize()
        viewModel.close()
        assertNull(viewModel.state.value.channel)

        viewModel.play("sky-sports-cricket", queue)
        assertEquals(TvPresentation.DOCKED, viewModel.state.value.presentation)
    }

    @Test
    fun `an unknown channel plays nothing`() {
        viewModel.play("gone")
        assertNull(viewModel.state.value.channel)
        viewModel.minimize()
        assertEquals(TvPresentation.DOCKED, viewModel.state.value.presentation)
    }
}
