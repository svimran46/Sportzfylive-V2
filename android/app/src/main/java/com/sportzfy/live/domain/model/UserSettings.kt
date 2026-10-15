package com.sportzfy.live.domain.model

/** Preferences the user sets on the Settings screen; kept on the device. */
data class UserSettings(
    /** Stream language picked first on the player; null means any language. */
    val preferredLanguage: String? = DEFAULT_LANGUAGE,
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
) {
    companion object {
        const val DEFAULT_LANGUAGE = "English"

        /** Languages offered on Settings, in Streamed's spelling. */
        val LANGUAGES = listOf(
            "English", "Spanish", "Portuguese", "French", "German", "Italian",
            "Arabic", "Hindi", "Russian", "Polish", "Turkish", "Dutch",
        )
    }
}

/** How kick-off times are written. */
enum class ClockFormat(val label: String) {
    /** Follows the device's 12/24-hour setting. */
    SYSTEM("System"),
    H12("12-hour"),
    H24("24-hour"),
}
