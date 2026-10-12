package com.sportzfy.live.presentation.model

import com.sportzfy.live.domain.model.Match
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Which slice of the fixture list a screen shows. */
enum class DayScope { TODAY, TOMORROW, ALL }

/**
 * Local-calendar boundaries in [zone] (the device timezone in the app).
 * "Today" is [todayStart, tomorrowStart); "tomorrow" is
 * [tomorrowStart, dayAfterStart). Built with calendar arithmetic, so month
 * and year changes and 23/25-hour DST days are handled.
 */
data class DayWindow(
    val todayStart: Long,
    val tomorrowStart: Long,
    val dayAfterStart: Long,
    val yesterdayStart: Long,
    val zone: TimeZone,
) {
    /** Local midnight that starts the day containing [millis]. */
    fun startOfDay(millis: Long): Long = calendarAt(millis, zone).timeInMillis

    fun dateLabel(dayStart: Long, locale: Locale): String =
        SimpleDateFormat("EEE d MMM", locale).apply { timeZone = zone }.format(Date(dayStart))

    companion object {
        fun at(nowMillis: Long, zone: TimeZone): DayWindow {
            val calendar = calendarAt(nowMillis, zone)
            val today = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            val yesterday = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 2)
            val tomorrow = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            return DayWindow(today, tomorrow, calendar.timeInMillis, yesterday, zone)
        }

        private fun calendarAt(millis: Long, zone: TimeZone): Calendar = Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}

/** The single place that decides which matches belong to which day. */
object MatchDays {

    /**
     * Live right now (whatever day it started), kicking off today, or with no
     * announced kick-off time.
     */
    fun today(matches: List<Match>, window: DayWindow): List<Match> = matches.filter {
        it.isLive || it.startTimeMillis == 0L || it.startTimeMillis in window.todayStart until window.tomorrowStart
    }

    /** Kicking off tomorrow and not already live. */
    fun tomorrow(matches: List<Match>, window: DayWindow): List<Match> = matches.filter {
        !it.isLive && it.startTimeMillis in window.tomorrowStart until window.dayAfterStart
    }

    fun inScope(matches: List<Match>, scope: DayScope, window: DayWindow): List<Match> = when (scope) {
        DayScope.TODAY -> today(matches, window)
        DayScope.TOMORROW -> tomorrow(matches, window)
        DayScope.ALL -> matches
    }

    /**
     * All Matches layout: live first, then one section per local day in
     * date order, then matches with no announced time. Each match appears
     * exactly once.
     */
    fun sectionsByDay(matches: List<Match>, window: DayWindow, locale: Locale): List<MatchSection> {
        val sections = mutableListOf<MatchSection>()
        val live = matches.filter { it.isLive }
        if (live.isNotEmpty()) sections += MatchSection(SectionKind.LIVE, live)

        val dated = matches.filter { !it.isLive && it.startTimeMillis > 0L }
        dated.groupBy { window.startOfDay(it.startTimeMillis) }
            .toSortedMap()
            .forEach { (dayStart, dayMatches) ->
                sections += MatchSection(
                    kind = null,
                    matches = dayMatches.sortedBy { it.startTimeMillis },
                    key = "day-$dayStart",
                    title = dayTitle(dayStart, window, locale),
                )
            }

        val undated = matches.filter { !it.isLive && it.startTimeMillis <= 0L }
        if (undated.isNotEmpty()) {
            sections += MatchSection(kind = null, matches = undated, key = "day-tba", title = "Time to be announced")
        }
        return sections
    }

    private fun dayTitle(dayStart: Long, window: DayWindow, locale: Locale): String = when (dayStart) {
        window.todayStart -> "Today"
        window.tomorrowStart -> "Tomorrow"
        window.yesterdayStart -> "Yesterday"
        else -> window.dateLabel(dayStart, locale)
    }
}
