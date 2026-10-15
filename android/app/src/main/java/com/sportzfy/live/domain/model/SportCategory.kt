package com.sportzfy.live.domain.model

/**
 * Sports exposed by the Streamed API. [apiKey] is the exact `category` string
 * upstream sends; anything unrecognised maps to [OTHER] so a new upstream sport
 * never breaks ingestion.
 */
enum class SportCategory(val apiKey: String, val displayName: String) {
    FOOTBALL("football", "Football"),
    CRICKET("cricket", "Cricket"),
    BASKETBALL("basketball", "Basketball"),
    TENNIS("tennis", "Tennis"),
    MOTORSPORT("motor-sports", "Motorsport"),
    AMERICAN_FOOTBALL("american-football", "American Football"),
    HOCKEY("hockey", "Hockey"),
    BASEBALL("baseball", "Baseball"),
    FIGHT("fight", "Fight"),
    RUGBY("rugby", "Rugby"),
    GOLF("golf", "Golf"),
    DARTS("darts", "Darts"),
    BILLIARDS("billiards", "Billiards"),
    AFL("afl", "AFL"),
    OTHER("other", "Other");

    companion object {
        private val byKey = entries.associateBy { it.apiKey }

        fun fromApiKey(key: String?): SportCategory {
            val normalized = key?.trim()?.lowercase()?.replace('_', '-') ?: return OTHER
            return byKey[normalized] ?: when (normalized) {
                "soccer" -> FOOTBALL
                "motorsport", "motorsports", "motor-sport", "f1" -> MOTORSPORT
                "mma", "boxing", "ufc" -> FIGHT
                else -> OTHER
            }
        }

        /** Primary filter pills shown on the catalog, in display order. */
        val featured: List<SportCategory> = listOf(FOOTBALL, CRICKET, BASKETBALL, TENNIS, MOTORSPORT)
    }
}
