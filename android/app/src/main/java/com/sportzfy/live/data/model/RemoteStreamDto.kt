package com.sportzfy.live.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Wire shape of one entry of `GET /api/stream/{source}/{id}`. Fields are raw
 * primitives because upstream mixes types (`"hd": "true"`, numeric strings).
 * Upstream also sends `embedUrl`, which is deliberately not modelled: playback
 * URLs are always rebuilt from the deterministic embed pattern.
 */
@Serializable
data class RemoteStreamDto(
    val streamNo: JsonPrimitive? = null,
    @SerialName("stream_no") val streamNoSnake: JsonPrimitive? = null,
    val language: JsonPrimitive? = null,
    val lang: JsonPrimitive? = null,
    val hd: JsonPrimitive? = null,
    val isHd: JsonPrimitive? = null,
    val quality: JsonPrimitive? = null,
    val label: JsonPrimitive? = null,
)
