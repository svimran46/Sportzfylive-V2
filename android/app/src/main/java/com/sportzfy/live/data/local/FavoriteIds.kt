package com.sportzfy.live.data.local

/**
 * Saved ids (matches, starred and recent channels), newest first. Match ids
 * only live for a day upstream, so the lists are capped instead of growing forever.
 */
object FavoriteIds {
    const val MAX_SAVED = 200
    private const val SEPARATOR = "\n"

    fun toggle(ids: List<String>, id: String, max: Int = MAX_SAVED): List<String> =
        if (id in ids) ids - id else (listOf(id) + ids).take(max)

    /** Puts [id] first, removing an older copy, so the list reads most recent first. */
    fun bump(ids: List<String>, id: String, max: Int = MAX_SAVED): List<String> =
        (listOf(id) + (ids - id)).take(max)

    fun encode(ids: List<String>): String = ids.joinToString(SEPARATOR)

    fun decode(raw: String?): List<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
}
