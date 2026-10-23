package com.meolist.domain.repeat

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A weekday, optionally restricted to its [ordinal]-th occurrence in the month (1..5, or -1..-5 from the end). */
data class WeekdayNum(val day: DayOfWeek, val ordinal: Int = 0) {
    init {
        require(ordinal in -5..5) { "ordinal out of range: $ordinal" }
    }

    override fun toString(): String = (if (ordinal != 0) ordinal.toString() else "") + CODES.getValue(day)

    companion object {
        internal val CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val BY_CODE = CODES.entries.associate { (k, v) -> v to k }

        fun parse(text: String): WeekdayNum? {
            val t = text.trim().uppercase()
            if (t.length < 2) return null
            val day = BY_CODE[t.takeLast(2)] ?: return null
            val prefix = t.dropLast(2).removePrefix("+")
            val ordinal = if (prefix.isEmpty()) 0 else prefix.toIntOrNull() ?: return null
            if (ordinal !in -5..5) return null
            return WeekdayNum(day, ordinal)
        }
    }
}

/**
 * A subset of RFC 5545 RRULE: FREQ, INTERVAL, BYDAY, BYMONTHDAY, BYMONTH, COUNT and UNTIL.
 *
 * [count] is the number of occurrences still to come, counting the current one. A task that
 * repeats with COUNT=1 is on its last occurrence. [until] is inclusive.
 */
data class RepeatRule(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val count: Int? = null,
    val until: LocalDate? = null,
) {
    init {
        require(interval >= 1) { "interval must be >= 1" }
        require(byMonthDay.all { it in -31..31 && it != 0 }) { "invalid BYMONTHDAY" }
        require(byMonth.all { it in 1..12 }) { "invalid BYMONTH" }
        require(count == null || count >= 1) { "count must be >= 1" }
    }

    fun toRRule(): String = buildList {
        add("FREQ=${freq.name}")
        if (interval != 1) add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) add("BYDAY=" + byDay.joinToString(","))
        if (byMonthDay.isNotEmpty()) add("BYMONTHDAY=" + byMonthDay.joinToString(","))
        if (byMonth.isNotEmpty()) add("BYMONTH=" + byMonth.joinToString(","))
        count?.let { add("COUNT=$it") }
        until?.let { add("UNTIL=" + it.format(DateTimeFormatter.BASIC_ISO_DATE)) }
    }.joinToString(";")

    override fun toString(): String = toRRule()

    companion object {
        /** Parses an RRULE string (an optional `RRULE:` prefix is accepted). Returns null if it is invalid. */
        fun parse(text: String?): RepeatRule? {
            if (text.isNullOrBlank()) return null
            val parts = text.trim().removePrefix("RRULE:").split(';').filter { it.isNotBlank() }
            val map = parts.associate { part ->
                val i = part.indexOf('=')
                if (i <= 0) return null
                part.substring(0, i).trim().uppercase() to part.substring(i + 1).trim()
            }
            return runCatching {
                val freq = Freq.valueOf(map["FREQ"]?.uppercase() ?: return null)
                RepeatRule(
                    freq = freq,
                    interval = map["INTERVAL"]?.toInt() ?: 1,
                    byDay = map["BYDAY"]?.split(',')?.map { WeekdayNum.parse(it) ?: return null }.orEmpty(),
                    byMonthDay = map["BYMONTHDAY"]?.split(',')?.map { it.trim().toInt() }.orEmpty(),
                    byMonth = map["BYMONTH"]?.split(',')?.map { it.trim().toInt() }.orEmpty(),
                    count = map["COUNT"]?.toInt(),
                    until = map["UNTIL"]?.let { LocalDate.parse(it.take(8), DateTimeFormatter.BASIC_ISO_DATE) },
                )
            }.getOrNull()
        }

        fun daily(interval: Int = 1) = RepeatRule(Freq.DAILY, interval)
        fun weekly(vararg days: DayOfWeek, interval: Int = 1) =
            RepeatRule(Freq.WEEKLY, interval, byDay = days.map { WeekdayNum(it) })
        fun monthly(interval: Int = 1) = RepeatRule(Freq.MONTHLY, interval)
        fun yearly(interval: Int = 1) = RepeatRule(Freq.YEARLY, interval)
        val WEEKDAYS: RepeatRule = weekly(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
    }
}
