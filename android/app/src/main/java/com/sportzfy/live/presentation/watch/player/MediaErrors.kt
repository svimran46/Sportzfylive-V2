package com.sportzfy.live.presentation.watch.player

import androidx.media3.common.PlaybackException

private val DRM_ERRORS = PlaybackException.ERROR_CODE_DRM_UNSPECIFIED until 7000

/** What to tell the viewer about a Media3 failure, from [PlaybackException.errorCode]. */
internal fun playerErrorFor(errorCode: Int): PlayerError = when (errorCode) {
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    -> PlayerError.UNSUPPORTED_FORMAT
    in DRM_ERRORS -> PlayerError.DRM_PROTECTED
    else -> PlayerError.PLAYBACK_FAILED
}
