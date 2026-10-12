package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchStatus
import com.sportzfy.live.domain.model.StreamItem

object WatchLogic {

    const val RELATED_LIMIT = 8

    /** Default pick: English HD, then any HD, then the first stream. */
    fun preferredStream(streams: List<StreamItem>): StreamItem? =
        streams.firstOrNull { it.isHd && it.language.equals("English", ignoreCase = true) }
            ?: streams.firstOrNull { it.isHd }
            ?: streams.firstOrNull()

    /**
     * Other matches worth switching to: not ended, same sport first, then
     * live before upcoming, then by kick-off.
     */
    fun related(all: List<Match>, current: Match?, limit: Int = RELATED_LIMIT): List<Match> =
        all.asSequence()
            .filter { it.id != current?.id && it.status != MatchStatus.ENDED }
            .sortedWith(
                compareBy<Match>(
                    { if (current != null && it.category == current.category) 0 else 1 },
                    { if (it.isLive) 0 else 1 },
                    { it.startTimeMillis },
                )
            )
            .take(limit)
            .toList()

    /** "Stream 1 HD • English": streams are numbered across all sources. */
    fun streamLabel(index: Int, item: StreamItem): String = buildString {
        append("Stream ").append(index + 1)
        if (item.isHd) append(" HD")
        if (item.language.isNotBlank()) append(" • ").append(item.language)
    }
}
