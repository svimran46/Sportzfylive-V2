package com.sportzfy.live.data.channels

import com.sportzfy.live.domain.model.Channel
import com.sportzfy.live.domain.model.ChannelCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** One entry of `channels.json`. `category` is optional and overrides name-based grouping. */
@Serializable
internal data class ChannelDto(
    val name: String? = null,
    val logo: String? = null,
    val genre: String? = null,
    val url: String? = null,
    val category: String? = null,
)

/**
 * Reads the channel directory. The file is a JavaScript snippet
 * (`const channels = [...];`), so the outermost JSON array is cut out of it
 * first; a plain JSON array works too.
 *
 * Entries without a name or an https stream URL are skipped, as are repeated
 * stream URLs. `genre` is "Sports" for every channel upstream (cartoons
 * included), so it can't group channels: [categoryOf] uses an explicit
 * `category` field when present, otherwise the channel name.
 */
object ChannelParser {

    private val CRICKET = Regex("""\b(cricket|willow)\b""")
    private val FOOTBALL = Regex(
        """\b(football|fussball|fußball|soccer|laliga|la liga|bundesliga|premier league|serie a|ligue 1|eredivisie)\b"""
    )

    /** Returns null when [raw] holds no readable JSON array. */
    fun parse(json: Json, raw: String): List<Channel>? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        val array = runCatching { json.parseToJsonElement(raw.substring(start, end + 1)) }.getOrNull() as? JsonArray
            ?: return null

        val seenUrls = HashSet<String>()
        val idCounts = HashMap<String, Int>()
        return array.mapNotNull { entry ->
            val dto = runCatching { json.decodeFromJsonElement(ChannelDto.serializer(), entry) }.getOrNull()
                ?: return@mapNotNull null
            val name = dto.name?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val url = dto.url?.trim()?.takeIf { isHttps(it) } ?: return@mapNotNull null
            if (!seenUrls.add(url)) return@mapNotNull null

            val base = slug(name).ifEmpty { "channel" }
            val count = (idCounts[base] ?: 0) + 1
            idCounts[base] = count
            Channel(
                id = if (count == 1) base else "$base-$count",
                name = name,
                logoUrl = dto.logo?.let(::decodeEntities)?.trim()?.takeIf { isHttps(it) },
                category = categoryOf(dto.category, name),
                streamUrl = url,
            )
        }
    }

    fun categoryOf(explicit: String?, name: String): ChannelCategory {
        when (explicit?.trim()?.lowercase()) {
            "football", "soccer" -> return ChannelCategory.FOOTBALL
            "cricket" -> return ChannelCategory.CRICKET
            "other", "others" -> return ChannelCategory.OTHERS
        }
        val lower = name.lowercase()
        return when {
            CRICKET.containsMatchIn(lower) -> ChannelCategory.CRICKET
            FOOTBALL.containsMatchIn(lower) -> ChannelCategory.FOOTBALL
            else -> ChannelCategory.OTHERS
        }
    }

    /** Undoes HTML escaping, including the repeated `&amp;amp;…` chains upstream logos carry. */
    internal fun decodeEntities(value: String): String {
        var current = value
        var rounds = 0
        while ("&amp;" in current && rounds++ < 32) current = current.replace("&amp;", "&")
        return current.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    }

    private fun isHttps(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) && url.length > "https://".length && url.none(Char::isWhitespace)

    private fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
