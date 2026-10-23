package com.meolist.domain.tracker

import com.meolist.domain.common.startOfDayMillis
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Period boundaries and duration sums for goals and statistics. */
object TrackerTime {
    fun periodStart(period: GoalPeriod, day: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = when (period) {
        GoalPeriod.DAY -> day
        GoalPeriod.WEEK -> day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        GoalPeriod.MONTH -> day.withDayOfMonth(1)
    }

    /** First day after the period that starts on [start]. */
    fun periodEnd(period: GoalPeriod, start: LocalDate): LocalDate = when (period) {
        GoalPeriod.DAY -> start.plusDays(1)
        GoalPeriod.WEEK -> start.plusWeeks(1)
        GoalPeriod.MONTH -> start.plusMonths(1)
    }

    /** The period containing [day] as [from, to) epoch milliseconds. */
    fun periodRange(period: GoalPeriod, day: LocalDate, firstDayOfWeek: DayOfWeek, zone: ZoneId): Pair<Long, Long> {
        val start = periodStart(period, day, firstDayOfWeek)
        return start.startOfDayMillis(zone) to periodEnd(period, start).startOfDayMillis(zone)
    }

    fun overlapMs(start: Long, end: Long, from: Long, to: Long): Long =
        (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)

    /** Time inside [from, to) covered by [records] plus [running] timers measured up to [now]. */
    fun totalMs(records: List<TimeRecord>, running: List<RunningTimer>, from: Long, to: Long, now: Long): Long =
        records.sumOf { overlapMs(it.startAt, it.endAt, from, to) } +
            running.sumOf { overlapMs(it.startedAt, now, from, to) }
}
