package com.meolist.domain.tracker

import com.meolist.domain.common.startOfDayMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class TrackerTimeTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val wednesday = LocalDate.of(2026, 10, 21)

    @Test
    fun weekStart_followsFirstDayOfWeek() {
        assertEquals(LocalDate.of(2026, 10, 19), TrackerTime.periodStart(GoalPeriod.WEEK, wednesday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 10, 18), TrackerTime.periodStart(GoalPeriod.WEEK, wednesday, DayOfWeek.SUNDAY))
        // The first day of the week starts its own week.
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(monday, TrackerTime.periodStart(GoalPeriod.WEEK, monday, DayOfWeek.MONDAY))
    }

    @Test
    fun monthRange_coversWholeMonth() {
        val (from, to) = TrackerTime.periodRange(GoalPeriod.MONTH, LocalDate.of(2028, 2, 15), DayOfWeek.MONDAY, zone)
        assertEquals(LocalDate.of(2028, 2, 1).startOfDayMillis(zone), from)
        assertEquals(LocalDate.of(2028, 3, 1).startOfDayMillis(zone), to)
    }

    @Test
    fun totalMs_clipsRecordsAndRunningTimersToRange() {
        val (from, to) = TrackerTime.periodRange(GoalPeriod.DAY, wednesday, DayOfWeek.MONDAY, zone)
        val hour = 3_600_000L
        val records = listOf(
            TimeRecord(typeId = 1, startAt = from - hour, endAt = from + hour), // 1h inside
            TimeRecord(typeId = 1, startAt = from + 5 * hour, endAt = from + 6 * hour), // 1h
            TimeRecord(typeId = 1, startAt = to + hour, endAt = to + 2 * hour), // outside
        )
        val running = listOf(RunningTimer(typeId = 2, startedAt = to - hour))
        assertEquals(3 * hour, TrackerTime.totalMs(records, running, from, to, now = to + hour))
    }
}
