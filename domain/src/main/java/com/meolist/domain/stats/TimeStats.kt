package com.meolist.domain.stats

import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TrackerTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class StatsPeriod { DAY, WEEK, MONTH, YEAR }

/** Calendar days [start, endExclusive) covered by one statistics page. */
data class StatsRange(val period: StatsPeriod, val start: LocalDate, val endExclusive: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()
    operator fun contains(day: LocalDate): Boolean = day >= start && day < endExclusive
}

/**
 * One bar of the chart. Days are split into local hours, weeks and months into days and years into
 * months. [hour] is set for hourly buckets only; [date] is the first day the bucket covers.
 */
data class StatsBucket(
    val from: Long,
    val to: Long,
    val date: LocalDate,
    val hour: Int? = null,
    val perType: Map<Long, Long> = emptyMap(),
) {
    val totalMs: Long get() = perType.values.sum()
}

data class TypeTotal(val typeId: Long, val ms: Long)

data class TimeStats(
    val range: StatsRange,
    val totalMs: Long,
    /** Largest first. */
    val byType: List<TypeTotal>,
    val buckets: List<StatsBucket>,
    /** Days of the range that have started so far (the whole range once it is over). */
    val elapsedDays: Int,
    val activeDays: Int,
) {
    val dailyAverageMs: Long get() = if (elapsedDays > 0) totalMs / elapsedDays else 0
}

/** Statistics over tracked time. Pure functions, so they are tested without Android. */
object TimeStatsCalculator {
    fun range(period: StatsPeriod, anchor: LocalDate, firstDayOfWeek: DayOfWeek): StatsRange = when (period) {
        StatsPeriod.DAY -> StatsRange(period, anchor, anchor.plusDays(1))
        StatsPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).let {
            StatsRange(period, it, it.plusWeeks(1))
        }
        StatsPeriod.MONTH -> anchor.withDayOfMonth(1).let { StatsRange(period, it, it.plusMonths(1)) }
        StatsPeriod.YEAR -> anchor.withDayOfYear(1).let { StatsRange(period, it, it.plusYears(1)) }
    }

    /** The range [steps] pages before (negative) or after (positive) [range]. */
    fun shift(range: StatsRange, steps: Long, firstDayOfWeek: DayOfWeek): StatsRange {
        val anchor = when (range.period) {
            StatsPeriod.DAY -> range.start.plusDays(steps)
            StatsPeriod.WEEK -> range.start.plusWeeks(steps)
            StatsPeriod.MONTH -> range.start.plusMonths(steps)
            StatsPeriod.YEAR -> range.start.plusYears(steps)
        }
        return range(range.period, anchor, firstDayOfWeek)
    }

    fun millis(range: StatsRange, zone: ZoneId): Pair<Long, Long> =
        range.start.startOfDayMillis(zone) to range.endExclusive.startOfDayMillis(zone)

    /** Empty buckets for [range]. Hourly buckets follow real instants, so DST days have 23 or 25. */
    fun buckets(range: StatsRange, zone: ZoneId): List<StatsBucket> = when (range.period) {
        StatsPeriod.DAY -> {
            val startOfDay = range.start.atStartOfDay(zone)
            val end = range.endExclusive.startOfDayMillis(zone)
            generateSequence(startOfDay) { it.plusHours(1) }
                .map { it.toInstant().toEpochMilli() to it }
                .takeWhile { (from, _) -> from < end }
                .map { (from, zoned) ->
                    StatsBucket(from, minOf(zoned.plusHours(1).toInstant().toEpochMilli(), end), range.start, hour = zoned.hour)
                }
                .toList()
        }
        StatsPeriod.WEEK, StatsPeriod.MONTH -> (0 until range.days).map { i ->
            val day = range.start.plusDays(i.toLong())
            StatsBucket(day.startOfDayMillis(zone), day.plusDays(1).startOfDayMillis(zone), day)
        }
        StatsPeriod.YEAR -> (0 until 12).map { i ->
            val month = range.start.plusMonths(i.toLong())
            StatsBucket(month.startOfDayMillis(zone), month.plusMonths(1).startOfDayMillis(zone), month)
        }
    }

    /**
     * Totals for [range]. Records and running timers (counted up to [now]) are clipped to the range,
     * so time crossing midnight or a period boundary is split correctly. Records of [excludedTypes]
     * (e.g. deleted types) are ignored.
     */
    fun compute(
        range: StatsRange,
        records: List<TimeRecord>,
        running: List<RunningTimer>,
        now: Long,
        zone: ZoneId,
        today: LocalDate,
        excludedTypes: Set<Long> = emptySet(),
    ): TimeStats {
        val spans = records.filter { it.typeId !in excludedTypes }.map { Triple(it.typeId, it.startAt, it.endAt) } +
            running.filter { it.typeId !in excludedTypes }.map { Triple(it.typeId, it.startedAt, maxOf(now, it.startedAt)) }
        val buckets = buckets(range, zone).map { bucket ->
            val perType = HashMap<Long, Long>()
            for ((typeId, start, end) in spans) {
                val ms = TrackerTime.overlapMs(start, end, bucket.from, bucket.to)
                if (ms > 0) perType.merge(typeId, ms, Long::plus)
            }
            bucket.copy(perType = perType)
        }
        val byType = HashMap<Long, Long>()
        buckets.forEach { b -> b.perType.forEach { (id, ms) -> byType.merge(id, ms, Long::plus) } }

        val activeDays = when (range.period) {
            StatsPeriod.DAY -> if (byType.isEmpty()) 0 else 1
            StatsPeriod.WEEK, StatsPeriod.MONTH -> buckets.count { it.totalMs > 0 }
            StatsPeriod.YEAR -> activeDaysOf(spans, range, zone)
        }
        val elapsedDays = when {
            today < range.start -> 0
            today >= range.endExclusive -> range.days
            else -> ChronoUnit.DAYS.between(range.start, today).toInt() + 1
        }
        return TimeStats(
            range = range,
            totalMs = byType.values.sum(),
            byType = byType.map { TypeTotal(it.key, it.value) }.sortedWith(compareByDescending<TypeTotal> { it.ms }.thenBy { it.typeId }),
            buckets = buckets,
            elapsedDays = elapsedDays,
            activeDays = activeDays,
        )
    }

    private fun activeDaysOf(spans: List<Triple<Long, Long, Long>>, range: StatsRange, zone: ZoneId): Int {
        val (from, to) = millis(range, zone)
        val days = HashSet<LocalDate>()
        for ((_, start, end) in spans) {
            val s = maxOf(start, from)
            val e = minOf(end, to)
            if (e <= s) continue
            var day = java.time.Instant.ofEpochMilli(s).atZone(zone).toLocalDate()
            val last = java.time.Instant.ofEpochMilli(e - 1).atZone(zone).toLocalDate()
            while (day <= last) {
                days += day
                day = day.plusDays(1)
            }
        }
        return days.size
    }

    /** Relative change from [previous] to [current], or null when there is nothing to compare with. */
    fun changeRatio(current: Long, previous: Long): Double? =
        if (previous <= 0) null else (current - previous).toDouble() / previous
}
