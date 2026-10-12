package com.sportzfy.live.data.mapper

import com.sportzfy.live.data.model.RemoteStreamDto
import com.sportzfy.live.domain.model.StreamItem
import com.sportzfy.live.domain.model.StreamSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Port of the web worker's `sanitizeStreamedStreams`: accepts a bare array or
 * an object wrapping `streams` / `data`, reads only public metadata, and
 * de-duplicates by stream number, language and HD flag.
 */
object StreamMapper {

    private val HD_PATTERN = Regex("""(\bhd\b|fhd|4k)""", RegexOption.IGNORE_CASE)

    fun toStreamItems(json: Json, element: JsonElement?, group: StreamSource): List<StreamItem> {
        val entries: List<JsonElement> = when (element) {
            is JsonArray -> element
            is JsonObject -> (element["streams"] as? JsonArray) ?: (element["data"] as? JsonArray) ?: emptyList()
            else -> emptyList()
        }

        val seen = HashSet<String>()
        val items = ArrayList<StreamItem>(entries.size)
        entries.forEachIndexed { index, entry ->
            val dto = (entry as? JsonObject)?.let {
                runCatching { json.decodeFromJsonElement(RemoteStreamDto.serializer(), it) }.getOrNull()
            }
            val item = dto?.toItem(group, fallbackNo = index + 1)
                // Primitive or undecodable entries still count as a stream, with no metadata.
                ?: StreamItem(group.source, group.id, streamNo = index + 1, language = "", isHd = false)
            if (seen.add("${item.streamNo}|${item.language}|${item.isHd}")) items += item
        }
        return items
    }

    private fun RemoteStreamDto.toItem(group: StreamSource, fallbackNo: Int): StreamItem {
        val number = (streamNo.text() ?: streamNoSnake.text())
            ?.toDoubleOrNull()
            ?.toInt()
            ?.takeIf { it > 0 }
            ?: fallbackNo
        val qualityText = quality.text() ?: label.text() ?: ""
        return StreamItem(
            source = group.source,
            sourceId = group.id,
            streamNo = number,
            language = (language.text() ?: lang.text())?.trim().orEmpty(),
            isHd = hd.isTrue() || isHd.isTrue() || HD_PATTERN.containsMatchIn(qualityText),
        )
    }

    private fun JsonPrimitive?.text(): String? = this?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonPrimitive?.isTrue(): Boolean =
        this?.booleanOrNull == true || this?.contentOrNull.equals("true", ignoreCase = true)
}
