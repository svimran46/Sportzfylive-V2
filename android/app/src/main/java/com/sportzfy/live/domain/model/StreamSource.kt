package com.sportzfy.live.domain.model

/**
 * One source group of a match (e.g. `alpha` / `match-101`). A group resolves to
 * zero or more [StreamItem]s through `GET /api/stream/{source}/{id}`.
 */
data class StreamSource(
    val source: String,
    val id: String,
)
