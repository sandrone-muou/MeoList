package com.meolist.domain.stats

import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.TimeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimeStatsCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun at(day: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(day, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private val oct20 = LocalDate.of(2026, 10, 20) // Tuesday

    @Test
    fun range_followsPeriodAndWeekStart() {
        val calc = TimeStatsCalculator
        assertEquals(StatsRange(StatsPeriod.DAY, oct20, oct20.plusDays(1)), calc.range(StatsPeriod.DAY, oct20, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 10, 19), calc.range(StatsPeriod.WEEK, oct20, DayOfWeek.MONDAY).start)
        assertEquals(LocalDate.of(2026, 10, 18), calc.range(StatsPeriod.WEEK, oct20, DayOfWeek.SUNDAY).start)
        val month = calc.range(StatsPeriod.MONTH, oct20, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 1), month.start)
        assertEquals(31, month.days)
        val year = calc.range(StatsPeriod.YEAR, oct20, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 1, 1), year.start)
        assertEquals(365, year.days)
    }

    @Test
    fun shift_movesWholePages() {
        val jan31 = TimeStatsCalculator.range(StatsPeriod.MONTH, LocalDate.of(2024, 1, 31), DayOfWeek.MONDAY)
        val feb = TimeStatsCalculator.shift(jan31, 1, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2024, 2, 1), feb.start)
        assertEquals(29, feb.days)
        val week = TimeStatsCalculator.range(StatsPeriod.WEEK, oct20, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 12), TimeStatsCalculator.shift(week, -1, DayOfWeek.MONDAY).start)
    }

    @Test
    fun buckets_dayHasHoursAndDstDaysDiffer() {
        val day = TimeStatsCalculator.range(StatsPeriod.DAY, oct20, DayOfWeek.MONDAY)
        val hours = TimeStatsCalculator.buckets(day, zone)
        assertEquals(24, hours.size)
        assertEquals((0..23).toList(), hours.map { it.hour })

        val newYork = ZoneId.of("America/New_York")
        val springForward = TimeStatsCalculator.range(StatsPeriod.DAY, LocalDate.of(2026, 3, 8), DayOfWeek.MONDAY)
        assertEquals(23, TimeStatsCalculator.buckets(springForward, newYork).size)
        val fallBack = TimeStatsCalculator.range(StatsPeriod.DAY, LocalDate.of(2026, 11, 1), DayOfWeek.MONDAY)
        val fallBuckets = TimeStatsCalculator.buckets(fallBack, newYork)
        assertEquals(25, fallBuckets.size)
        assertEquals(25 * hour, fallBuckets.sumOf { it.to - it.from })

        val year = TimeStatsCalculator.range(StatsPeriod.YEAR, oct20, DayOfWeek.MONDAY)
        assertEquals(12, TimeStatsCalculator.buckets(year, zone).size)
    }

    @Test
    fun compute_clipsRecordsToRangeAndSplitsAcrossDays() {
        val week = TimeStatsCalculator.range(StatsPeriod.WEEK, oct20, DayOfWeek.MONDAY) // Oct 19..25
        val records = listOf(
            // 23:00 Sunday Oct 18 -> 01:00 Monday Oct 19: one hour inside the week.
            TimeRecord(typeId = 1, startAt = at(LocalDate.of(2026, 10, 18), 23), endAt = at(LocalDate.of(2026, 10, 19), 1)),
            // 22:00 Tue -> 02:00 Wed: split between two days.
            TimeRecord(typeId = 2, startAt = at(oct20, 22), endAt = at(oct20.plusDays(1), 2)),
            TimeRecord(typeId = 1, startAt = at(oct20, 9), endAt = at(oct20, 10, 30)),
        )
        val stats = TimeStatsCalculator.compute(week, records, emptyList(), now = at(oct20.plusDays(1), 12), zone = zone, today = oct20.plusDays(1))

        assertEquals(6 * hour + 30 * minute, stats.totalMs)
        assertEquals(listOf(4 * hour to 2L, 2 * hour + 30 * minute to 1L), stats.byType.map { it.ms to it.typeId })
        val perDay = stats.buckets.map { it.totalMs }
        assertEquals(listOf(hour, 3 * hour + 30 * minute, 2 * hour, 0L, 0L, 0L, 0L), perDay)
        assertEquals(mapOf(1L to hour + 30 * minute, 2L to 2 * hour), stats.buckets[1].perType)
        assertEquals(3, stats.activeDays)
        // Wednesday is the 3rd day of the week: the average divides by the days so far.
        assertEquals(3, stats.elapsedDays)
        assertEquals((6 * hour + 30 * minute) / 3, stats.dailyAverageMs)
    }

    @Test
    fun compute_includesRunningTimersUpToNow() {
        val day = TimeStatsCalculator.range(StatsPeriod.DAY, oct20, DayOfWeek.MONDAY)
        val running = listOf(RunningTimer(id = 1, typeId = 3, startedAt = at(oct20, 9, 30)))
        val stats = TimeStatsCalculator.compute(day, emptyList(), running, now = at(oct20, 11, 15), zone = zone, today = oct20)

        assertEquals(hour + 45 * minute, stats.totalMs)
        assertEquals(30 * minute, stats.buckets[9].totalMs)
        assertEquals(hour, stats.buckets[10].totalMs)
        assertEquals(15 * minute, stats.buckets[11].totalMs)
        assertEquals(0, stats.buckets[12].totalMs)
    }

    @Test
    fun compute_ignoresExcludedTypesAndCountsFutureRangesAsNotStarted() {
        val month = TimeStatsCalculator.range(StatsPeriod.MONTH, LocalDate.of(2026, 11, 5), DayOfWeek.MONDAY)
        val records = listOf(TimeRecord(typeId = 9, startAt = at(LocalDate.of(2026, 11, 2), 8), endAt = at(LocalDate.of(2026, 11, 2), 9)))
        val stats = TimeStatsCalculator.compute(month, records, emptyList(), at(oct20, 12), zone, oct20, excludedTypes = setOf(9))
        assertEquals(0, stats.totalMs)
        assertEquals(0, stats.elapsedDays)
        assertEquals(0, stats.dailyAverageMs)

        val past = TimeStatsCalculator.range(StatsPeriod.MONTH, LocalDate.of(2026, 9, 1), DayOfWeek.MONDAY)
        assertEquals(30, TimeStatsCalculator.compute(past, emptyList(), emptyList(), at(oct20, 12), zone, oct20).elapsedDays)
    }

    @Test
    fun compute_yearCountsActiveDistinctDays() {
        val year = TimeStatsCalculator.range(StatsPeriod.YEAR, oct20, DayOfWeek.MONDAY)
        val records = listOf(
            TimeRecord(typeId = 1, startAt = at(oct20, 22), endAt = at(oct20.plusDays(1), 1)),
            TimeRecord(typeId = 1, startAt = at(oct20, 8), endAt = at(oct20, 9)),
            TimeRecord(typeId = 1, startAt = at(LocalDate.of(2026, 2, 3), 8), endAt = at(LocalDate.of(2026, 2, 3), 9)),
        )
        val stats = TimeStatsCalculator.compute(year, records, emptyList(), at(oct20.plusDays(2), 0), zone, oct20.plusDays(2))
        assertEquals(3, stats.activeDays)
        assertEquals(hour, stats.buckets[1].totalMs)
        assertEquals(4 * hour, stats.buckets[9].totalMs)
    }

    @Test
    fun changeRatio_handlesEmptyPrevious() {
        assertNull(TimeStatsCalculator.changeRatio(10, 0))
        assertEquals(0.5, TimeStatsCalculator.changeRatio(15, 10)!!, 1e-9)
        assertEquals(-1.0, TimeStatsCalculator.changeRatio(0, 10)!!, 1e-9)
    }
}
