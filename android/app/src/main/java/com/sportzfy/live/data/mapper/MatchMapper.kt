package com.sportzfy.live.data.mapper

import com.sportzfy.live.core.network.StreamedEndpoints
import com.sportzfy.live.data.model.RemoteMatchDto
import com.sportzfy.live.data.model.RemoteTeamDto
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.domain.model.SportCategory
import com.sportzfy.live.domain.model.StreamSource
import com.sportzfy.live.domain.model.Team
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

object MatchMapper {

    /** Decodes a match list, dropping entries that fail to decode. */
    fun decodeList(json: Json, element: JsonElement): List<RemoteMatchDto> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { entry ->
            runCatching { json.decodeFromJsonElement(RemoteMatchDto.serializer(), entry) }.getOrNull()
        }
    }

    /**
     * Merges `all-today` with `live`. Matches in the live feed are tagged
     * [MatchStatus.LIVE]; live matches missing from `all-today` are appended.
     * [live] is null when the live feed failed to load: nothing is marked
     * live then, and matches that have kicked off are [MatchStatus.STARTED].
     */
    fun merge(today: List<RemoteMatchDto>, live: List<RemoteMatchDto>?, nowMillis: Long): List<Match> {
        val liveById = live
            ?.mapNotNull { dto -> dto.id?.trim()?.takeIf { it.isNotEmpty() }?.let { it to dto } }
            ?.toMap()
        val merged = LinkedHashMap<String, Match>()

        for (dto in today) {
            val id = dto.id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (id in merged) continue
            val liveDto = liveById?.get(id)
            val match = toDomain(dto, nowMillis, isLive = liveById?.let { liveDto != null }) ?: continue
            merged[id] = if (liveDto == null) match else match.copy(sources = (match.sources + toSources(liveDto)).distinct())
        }
        liveById?.forEach { (id, dto) ->
            if (id !in merged) toDomain(dto, nowMillis, isLive = true)?.let { merged[id] = it }
        }
        return merged.values.sortedWith(catalogOrder)
    }

    /** [isLive] null means the live feed is unknown. */
    fun toDomain(dto: RemoteMatchDto, nowMillis: Long, isLive: Boolean?): Match? {
        val id = dto.id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val home = dto.teams?.home?.toDomain()
        val away = dto.teams?.away?.toDomain()
        val title = dto.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (home != null && away != null) "${home.name} vs ${away.name}" else return null
        val start = dto.date?.takeIf { it > 0 } ?: 0L

        return Match(
            id = id,
            title = title,
            category = SportCategory.fromApiKey(dto.category),
            startTimeMillis = start,
            posterUrl = StreamedEndpoints.posterUrl(dto.poster),
            homeTeam = home,
            awayTeam = away,
            sources = toSources(dto),
            status = status(start, nowMillis, isLive),
            isPopular = dto.popular == true,
        )
    }

    private fun status(start: Long, now: Long, isLive: Boolean?): MatchStatus = when {
        isLive == true -> MatchStatus.LIVE
        start == 0L || start > now -> MatchStatus.UPCOMING
        // Only the live feed may say a match is live; kick-off time never does.
        isLive == null -> MatchStatus.STARTED
        else -> MatchStatus.ENDED
    }

    private fun RemoteTeamDto.toDomain(): Team? {
        val teamName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Team(name = teamName, badgeUrl = StreamedEndpoints.badgeUrl(badge))
    }

    private fun toSources(dto: RemoteMatchDto): List<StreamSource> =
        dto.sources.orEmpty()
            .mapNotNull { s ->
                val source = s.source?.trim().orEmpty()
                val id = s.id?.trim().orEmpty()
                if (source.isEmpty() || id.isEmpty()) null else StreamSource(source, id)
            }
            .distinct()

    /**
     * Live, then started, then upcoming by kick-off (no announced time last),
     * then ended (most recent first).
     */
    private val catalogOrder = Comparator<Match> { a, b ->
        val byStatus = a.status.ordinal.compareTo(b.status.ordinal)
        val aUntimed = a.startTimeMillis <= 0L
        val bUntimed = b.startTimeMillis <= 0L
        when {
            byStatus != 0 -> byStatus
            aUntimed != bUntimed -> if (aUntimed) 1 else -1
            a.status == MatchStatus.ENDED -> b.startTimeMillis.compareTo(a.startTimeMillis)
            else -> a.startTimeMillis.compareTo(b.startTimeMillis)
        }
    }
}
