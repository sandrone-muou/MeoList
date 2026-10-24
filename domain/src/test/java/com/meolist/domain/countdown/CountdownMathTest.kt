package com.meolist.domain.countdown

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownMathTest {
    private fun countdown(
        base: LocalDate,
        mode: CountdownMode = CountdownMode.DOWN,
        yearly: Boolean = false,
    ) = Countdown(id = 1, title = "X", baseDay = base, mode = mode, yearly = yearly)

    @Test
    fun down_countsDaysToTheDate() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals(7, CountdownMath.daysToTarget(countdown(LocalDate.of(2026, 10, 31)), today))
        assertEquals(0, CountdownMath.daysToTarget(countdown(today), today))
        assertEquals(-3, CountdownMath.daysToTarget(countdown(LocalDate.of(2026, 10, 21)), today))
    }

    @Test
    fun yearly_movesToTheNextAnniversary() {
        val today = LocalDate.of(2026, 10, 24)
        val c = countdown(LocalDate.of(1990, 3, 2), yearly = true)
        assertEquals(LocalDate.of(2027, 3, 2), CountdownMath.target(c, today))
        assertEquals(129, CountdownMath.daysToTarget(c, today))
        assertEquals(37, CountdownMath.anniversaryYears(c, today))
        // The anniversary itself is today.
        assertEquals(0, CountdownMath.daysToTarget(countdown(LocalDate.of(1990, 10, 24), yearly = true), today))
    }

    @Test
    fun yearly_leapDayFallsBackToFebruary28() {
        val leap = countdown(LocalDate.of(2000, 2, 29), yearly = true)
        // 2027 is not a leap year: the next anniversary after Mar 1 2026 is Feb 28 2027.
        assertEquals(LocalDate.of(2027, 2, 28), CountdownMath.target(leap, LocalDate.of(2026, 3, 1)))
        // 2028 has a leap day, so it is used again.
        assertEquals(LocalDate.of(2028, 2, 29), CountdownMath.target(leap, LocalDate.of(2027, 3, 1)))
        // The day before the anniversary keeps this year's February 28.
        assertEquals(LocalDate.of(2026, 2, 28), CountdownMath.target(leap, LocalDate.of(2026, 2, 27)))
    }

    @Test
    fun up_countsDaysAndYearsSince() {
        val today = LocalDate.of(2026, 10, 24)
        val c = countdown(LocalDate.of(2025, 10, 24), mode = CountdownMode.UP, yearly = true)
        assertEquals(365, CountdownMath.daysSince(c, today))
        assertEquals(1, CountdownMath.yearsSince(c, today))
        // The target is today's first anniversary.
        assertEquals(1, CountdownMath.anniversaryYears(c, today))
    }

    @Test
    fun modeParsingFallsBackToDown() {
        assertEquals(CountdownMode.UP, CountdownMode.fromValue(1))
        assertEquals(CountdownMode.DOWN, CountdownMode.fromValue(7))
    }
}
