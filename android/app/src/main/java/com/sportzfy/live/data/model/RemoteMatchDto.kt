package com.sportzfy.live.data.model

import kotlinx.serialization.Serializable

/**
 * Wire shape of `GET /api/matches/{all-today|live}`. Every field is optional:
 * upstream omits fields freely and one malformed match must not fail the list.
 */
@Serializable
data class RemoteMatchDto(
    val id: String? = null,
    val title: String? = null,
    val category: String? = null,
    /** Kick-off in epoch milliseconds. */
    val date: Long? = null,
    /** Site-relative path (`/api/images/poster/...`) or absolute URL. */
    val poster: String? = null,
    val popular: Boolean? = null,
    val teams: RemoteTeamsDto? = null,
    val sources: List<RemoteSourceDto>? = null,
)

@Serializable
data class RemoteTeamsDto(
    val home: RemoteTeamDto? = null,
    val away: RemoteTeamDto? = null,
)

@Serializable
data class RemoteTeamDto(
    val name: String? = null,
    /** Badge token (resolved under `/api/images/badge/{token}.webp`) or absolute URL. */
    val badge: String? = null,
)

@Serializable
data class RemoteSourceDto(
    val source: String? = null,
    val id: String? = null,
)
