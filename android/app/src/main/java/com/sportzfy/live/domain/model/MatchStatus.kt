package com.sportzfy.live.domain.model

/** Declaration order is the catalog sort order. */
enum class MatchStatus {
    /** Present in the upstream `live` feed right now. */
    LIVE,

    /**
     * Kick-off has passed, but the `live` feed couldn't be loaded, so whether
     * it is still on is unknown. Never shown as live.
     */
    STARTED,

    /** Kick-off is in the future, or not announced yet. */
    UPCOMING,

    /** Kick-off has passed and the match is not in the live feed. */
    ENDED,
}
