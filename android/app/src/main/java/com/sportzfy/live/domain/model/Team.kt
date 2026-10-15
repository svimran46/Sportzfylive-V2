package com.sportzfy.live.domain.model

data class Team(
    val name: String,
    /** Absolute https URL of the crest, or null when upstream has none. */
    val badgeUrl: String?,
) {
    /** Up to two initials, used as the fallback when the crest fails to load. */
    val initials: String
        get() = name.split(' ', '-', '.')
            .filter { it.isNotBlank() && it.first().isLetterOrDigit() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}
