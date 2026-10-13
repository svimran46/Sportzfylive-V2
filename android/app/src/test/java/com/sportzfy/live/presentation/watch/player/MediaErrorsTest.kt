package com.sportzfy.live.presentation.watch.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaErrorsTest {

    @Test
    fun `codec and container failures say the format is unsupported`() {
        for (code in listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        )) {
            assertEquals(PlayerError.UNSUPPORTED_FORMAT, playerErrorFor(code))
        }
    }

    @Test
    fun `DRM failures say the stream is protected`() {
        assertEquals(PlayerError.DRM_PROTECTED, playerErrorFor(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED))
        assertEquals(PlayerError.DRM_PROTECTED, playerErrorFor(PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED))
    }

    @Test
    fun `network and server failures stay retryable`() {
        for (code in listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )) {
            assertEquals(PlayerError.PLAYBACK_FAILED, playerErrorFor(code))
        }
    }
}
