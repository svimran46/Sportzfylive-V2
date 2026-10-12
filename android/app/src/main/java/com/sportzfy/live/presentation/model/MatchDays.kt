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
            val today = calendarAt(nowMillis, zone)
            // Each boundary is found from its own date: where DST skips
            // midnight (Chile, Cairo, Havana) a day starts at 01:00, and that
            // hour must not carry over to the neighbouring days.
            fun dayStart(offset: Int): Long = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return DayWindow(
                todayStart = today.timeInMillis,
                tomorrowStart = dayStart(1),
                dayAfterStart = dayStart(2),
                yesterdayStart = dayStart(-1),
                zone = zone,
            )
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
