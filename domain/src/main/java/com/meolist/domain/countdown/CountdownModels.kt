package com.meolist.domain.countdown

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow

enum class CountdownMode(val value: Int) {
    /** Counts down to [Countdown.baseDay] (or its next anniversary when yearly). */
    DOWN(0),
    /** Counts up from [Countdown.baseDay], e.g. "365 days since". */
    UP(1);

    companion object {
        fun fromValue(value: Int): CountdownMode = entries.firstOrNull { it.value == value } ?: DOWN
    }
}

/** A date to look forward to or a milestone to remember. */
data class Countdown(
    val id: Long = 0,
    val title: String,
    val baseDay: LocalDate,
    val mode: CountdownMode = CountdownMode.DOWN,
    /** The date repeats every year; a Feb 29 date falls on Feb 28 in other years. */
    val yearly: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val color: Int = 0,
    val note: String = "",
    val sortOrder: Long = 0,
) {
    val isPast: Boolean get() = baseDay.isBefore(LocalDate.now())
}

interface CountdownRepository {
    fun observeAll(): Flow<List<Countdown>>
    suspend fun get(id: Long): Countdown?
    suspend fun save(countdown: Countdown): Long
    suspend fun delete(id: Long)
}

/** Date arithmetic for countdowns. Pure and test-friendly. */
object CountdownMath {
    /**
     * The date this countdown points at from [today] onwards. For yearly countdowns before today it
     * is the next anniversary (never in the past); otherwise it is the base date itself.
     */
    fun target(countdown: Countdown, today: LocalDate): LocalDate =
        if (!countdown.yearly || !countdown.baseDay.isBefore(today)) {
            countdown.baseDay
        } else {
            val thisYear = anniversaryIn(countdown.baseDay, today.year)
            if (!thisYear.isBefore(today)) thisYear else anniversaryIn(countdown.baseDay, today.year + 1)
        }

    /** Days from [today] to the target: 0 means today, negative means the date has passed. */
    fun daysToTarget(countdown: Countdown, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, target(countdown, today))

    fun daysSince(countdown: Countdown, today: LocalDate): Long =
        ChronoUnit.DAYS.between(countdown.baseDay, today)

    fun yearsSince(countdown: Countdown, today: LocalDate): Int =
        Period.between(countdown.baseDay, today).years

    /** Whole years the target anniversary marks; 0 for non-yearly countdowns. */
    fun anniversaryYears(countdown: Countdown, today: LocalDate): Int =
        if (!countdown.yearly) 0 else target(countdown, today).year - countdown.baseDay.year

    /** Feb 29 becomes Feb 28 in years without a leap day. */
    private fun anniversaryIn(base: LocalDate, year: Int): LocalDate = try {
        base.withYear(year)
    } catch (_: DateTimeException) {
        LocalDate.of(year, 2, 28)
    }
}
