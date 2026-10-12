package com.sportzfy.live.domain.model

enum class MatchStatus {
    /** Present in the upstream `live` feed right now. */
    LIVE,

    /** Kick-off is in the future. */
    UPCOMING,

    /** Kick-off has passed but the match is no longer in the live feed. */
    ENDED,
}
