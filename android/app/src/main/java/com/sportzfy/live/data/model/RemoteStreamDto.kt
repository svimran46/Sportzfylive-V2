package com.sportzfy.live.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of `GET /api/stream/{source}/{id}`. Upstream also sends
 * `embedUrl`, which is deliberately not modelled: playback URLs are always
 * rebuilt from the deterministic embed pattern.
 */
@Serializable
data class RemoteStreamDto(
    val streamNo: Int? = null,
    @SerialName("stream_no") val streamNoSnake: Int? = null,
    val language: String? = null,
    val lang: String? = null,
    val hd: Boolean? = null,
    val quality: String? = null,
    val label: String? = null,
    val source: String? = null,
)
