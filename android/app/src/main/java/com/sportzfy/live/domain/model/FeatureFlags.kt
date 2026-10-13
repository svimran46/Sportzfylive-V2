package com.sportzfy.live.domain.model

/**
 * App features the owner switches on or off from the admin "Android app"
 * page (`features` in the remote config). Every flag defaults to on, so a
 * config without it, or a build that predates it, behaves as before.
 */
data class FeatureFlags(
    /** Streams and channel pages served over plain http may play. */
    val httpStreams: Boolean = true,
    /** DASH and progressive (MPEG-TS, MP4…) streams play in Media3. HLS and web pages always do. */
    val extendedFormats: Boolean = true,
)
