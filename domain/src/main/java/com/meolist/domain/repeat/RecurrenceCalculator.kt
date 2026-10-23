package com.meolist.domain.repeat

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Expands [RepeatRule]s into dates. A series is anchored at a start date: the anchor fixes the
 * period grid used by INTERVAL and supplies defaults (weekday, day of month, month) that the
 * rule leaves out. Dates that do not exist in a period are skipped, as in RFC 5545
 * (monthly on the 31st skips 30-day months; yearly on Feb 29 only hits leap years).
 */
object RecurrenceCalculator {
    /** Upper bound on scanned periods, so impossible rules (e.g. Feb 30) terminate. */
    private const val MAX_PERIODS = 4000

    /** First occurrence strictly after [after] and not before [anchor]. Honors UNTIL; COUNT is the caller's job. */
    fun next(rule: RepeatRule, anchor: LocalDate, after: LocalDate): LocalDate? {
        val origin = periodStart(rule.freq, anchor)
        val from = maxOf(anchor, after)
        val firstIndex = (unitsBetween(rule.freq, origin, periodStart(rule.freq, from)) / rule.interval).coerceAtLeast(0)
        for (k in firstIndex until firstIndex + MAX_PERIODS) {
            val period = periodAt(rule, origin, k)
            if (rule.until != null && period > rule.until) return null
            for (date in candidates(rule, anchor, period)) {
                if (date <= after || date < anchor) continue
                if (rule.until != null && date > rule.until) return null
                return date
            }
        }
        return null
    }

    /**
     * Occurrences of the series anchored at [anchor] that fall within [from]..[to] (inclusive),
     * at most [limit]. COUNT is counted from the anchor's period.
     */
    fun occurrences(
        rule: RepeatRule,
        anchor: LocalDate,
        from: LocalDate,
        to: LocalDate,
        limit: Int = 500,
    ): List<LocalDate> {
        if (to < from) return emptyList()
        val origin = periodStart(rule.freq, anchor)
        // Without COUNT we can jump straight to the first relevant period.
        val firstIndex = if (rule.count != null) 0L
        else (unitsBetween(rule.freq, origin, periodStart(rule.freq, maxOf(anchor, from))) / rule.interval).coerceAtLeast(0)
        var remaining = rule.count ?: Int.MAX_VALUE
        val result = mutableListOf<LocalDate>()
        var scannedEmpty = 0
        var k = firstIndex
        while (remaining > 0 && result.size < limit) {
            val period = periodAt(rule, origin, k++)
            if (period > to || (rule.until != null && period > rule.until)) break
            val dates = candidates(rule, anchor, period).filter { it >= anchor && (rule.until == null || it <= rule.until) }
            if (dates.isEmpty()) {
                if (++scannedEmpty > MAX_PERIODS) break
                continue
            }
            scannedEmpty = 0
            for (date in dates) {
                if (remaining <= 0 || result.size >= limit) break
                remaining--
                if (date in from..to) result += date
            }
        }
        return result
    }

    /** "Repeat from completion": the next date is simply [interval] units after the completion date. */
    fun nextFromCompletion(rule: RepeatRule, completedOn: LocalDate): LocalDate = when (rule.freq) {
        Freq.DAILY -> completedOn.plusDays(rule.interval.toLong())
        Freq.WEEKLY -> completedOn.plusWeeks(rule.interval.toLong())
        Freq.MONTHLY -> completedOn.plusMonths(rule.interval.toLong())
        Freq.YEARLY -> completedOn.plusYears(rule.interval.toLong())
    }

    private fun periodStart(freq: Freq, date: LocalDate): LocalDate = when (freq) {
        Freq.DAILY -> date
        Freq.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Freq.MONTHLY -> date.withDayOfMonth(1)
        Freq.YEARLY -> date.withDayOfYear(1)
    }

    private fun unitsBetween(freq: Freq, a: LocalDate, b: LocalDate): Long = when (freq) {
        Freq.DAILY -> ChronoUnit.DAYS.between(a, b)
        Freq.WEEKLY -> ChronoUnit.WEEKS.between(a, b)
        Freq.MONTHLY -> ChronoUnit.MONTHS.between(a, b)
        Freq.YEARLY -> ChronoUnit.YEARS.between(a, b)
    }

    private fun periodAt(rule: RepeatRule, origin: LocalDate, index: Long): LocalDate {
        val n = index * rule.interval
        return when (rule.freq) {
            Freq.DAILY -> origin.plusDays(n)
            Freq.WEEKLY -> origin.plusWeeks(n)
            Freq.MONTHLY -> origin.plusMonths(n)
            Freq.YEARLY -> origin.plusYears(n)
        }
    }

    /** Sorted candidate dates inside the period starting at [period]. */
    private fun candidates(rule: RepeatRule, anchor: LocalDate, period: LocalDate): List<LocalDate> = when (rule.freq) {
        Freq.DAILY -> listOf(period).filter { d ->
            (rule.byMonth.isEmpty() || d.monthValue in rule.byMonth) &&
                (rule.byDay.isEmpty() || rule.byDay.any { it.day == d.dayOfWeek }) &&
                (rule.byMonthDay.isEmpty() || matchesMonthDay(d, rule.byMonthDay))
        }
        Freq.WEEKLY -> {
            val days = rule.byDay.map { it.day }.ifEmpty { listOf(anchor.dayOfWeek) }.distinct()
            days.map { period.plusDays((it.value - 1).toLong()) }
                .filter { rule.byMonth.isEmpty() || it.monthValue in rule.byMonth }
                .sorted()
        }
        Freq.MONTHLY -> {
            val ym = YearMonth.from(period)
            if (rule.byMonth.isNotEmpty() && ym.monthValue !in rule.byMonth) emptyList() else datesInMonth(rule, anchor, ym)
        }
        Freq.YEARLY -> {
            val months = rule.byMonth.ifEmpty { listOf(anchor.monthValue) }.distinct().sorted()
            months.flatMap { datesInMonth(rule, anchor, YearMonth.of(period.year, it)) }
        }
    }

    private fun datesInMonth(rule: RepeatRule, anchor: LocalDate, ym: YearMonth): List<LocalDate> {
        val length = ym.lengthOfMonth()
        val dates = when {
            rule.byDay.isNotEmpty() -> rule.byDay.flatMap { wd ->
                val all = (1..length).map { ym.atDay(it) }.filter { it.dayOfWeek == wd.day }
                when {
                    wd.ordinal > 0 -> listOfNotNull(all.getOrNull(wd.ordinal - 1))
                    wd.ordinal < 0 -> listOfNotNull(all.getOrNull(all.size + wd.ordinal))
                    else -> all
                }
            }.filter { rule.byMonthDay.isEmpty() || matchesMonthDay(it, rule.byMonthDay) }
            rule.byMonthDay.isNotEmpty() -> rule.byMonthDay.mapNotNull { d ->
                val day = if (d > 0) d else length + d + 1
                if (day in 1..length) ym.atDay(day) else null
            }
            else -> if (anchor.dayOfMonth <= length) listOf(ym.atDay(anchor.dayOfMonth)) else emptyList()
        }
        return dates.distinct().sorted()
    }

    private fun matchesMonthDay(date: LocalDate, byMonthDay: List<Int>): Boolean {
        val length = date.lengthOfMonth()
        return byMonthDay.any { d -> (if (d > 0) d else length + d + 1) == date.dayOfMonth }
    }
}
