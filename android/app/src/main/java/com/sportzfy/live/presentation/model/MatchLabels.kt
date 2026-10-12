package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.DataError
import com.sportzfy.live.domain.model.Match
import com.sportzfy.live.domain.model.MatchStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Formats kick-off and status text. Uses java.text so it works below API 26. */
class MatchTimeFormatter(
    private val locale: Locale = Locale.getDefault(),
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val use24Hour: Boolean = true,
) {
    private val clock = SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", locale).apply { timeZone = this@MatchTimeFormatter.timeZone }
    private val day = SimpleDateFormat("EEE d MMM", locale).apply { timeZone = this@MatchTimeFormatter.timeZone }

    fun status(match: Match, nowMillis: Long): String = when (match.status) {
        MatchStatus.LIVE -> "LIVE"
        MatchStatus.ENDED -> "Ended"
        MatchStatus.UPCOMING -> kickoff(match.startTimeMillis, nowMillis)
    }

    fun kickoff(startMillis: Long, nowMillis: Long): String {
        if (startMillis <= 0L) return "Time TBA"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(startMillis - nowMillis)
        if (startMillis >= nowMillis && minutes < 60) return if (minutes < 1) "Starting now" else "In $minutes min"

        val time = clock.format(Date(startMillis))
        return when (dayOffset(startMillis, nowMillis)) {
            0 -> "Today $time"
            1 -> "Tomorrow $time"
            -1 -> "Yesterday $time"
            else -> "${day.format(Date(startMillis))} $time"
        }
    }

    /** Calendar days from [nowMillis] to [millis] in [timeZone]. */
    private fun dayOffset(millis: Long, nowMillis: Long): Int {
        fun dayStart(at: Long): Long = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = at
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diff = dayStart(millis) - dayStart(nowMillis)
        // Rounding absorbs the 23h/25h days around DST changes.
        return Math.round(diff / TimeUnit.DAYS.toMillis(1).toDouble()).toInt()
    }
}

fun sourcesLabel(count: Int): String = if (count == 1) "1 source" else "$count sources"

fun DataError.userMessage(): String = when (this) {
    DataError.NETWORK -> "You're offline. Check your connection."
    DataError.RATE_LIMITED -> "Too many requests. Try again in a minute."
    DataError.BLOCKED -> "The match server is refusing requests right now."
    DataError.UNAVAILABLE -> "The match server is down. Try again shortly."
    DataError.UNKNOWN -> "Something went wrong loading matches."
}
