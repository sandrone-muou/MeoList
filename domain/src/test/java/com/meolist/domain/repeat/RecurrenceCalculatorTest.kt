package com.meolist.domain.repeat

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceCalculatorTest {

    private fun d(text: String) = LocalDate.parse(text)

    private fun next(rule: String, anchor: String, after: String = anchor) =
        RecurrenceCalculator.next(RepeatRule.parse(rule)!!, d(anchor), d(after))

    @Test
    fun parse_roundTrip_andRejectsGarbage() {
        val text = "FREQ=MONTHLY;INTERVAL=2;BYDAY=-1FR,2MO;BYMONTH=1,7;COUNT=5;UNTIL=20271231"
        val rule = RepeatRule.parse(text)!!
        assertEquals(listOf(WeekdayNum(FRIDAY, -1), WeekdayNum(MONDAY, 2)), rule.byDay)
        assertEquals(d("2027-12-31"), rule.until)
        assertEquals(text, rule.toRRule())
        assertEquals(rule, RepeatRule.parse("RRULE:$text"))
        assertNull(RepeatRule.parse("FREQ=HOURLY"))
        assertNull(RepeatRule.parse("FREQ=DAILY;INTERVAL=0"))
        assertNull(RepeatRule.parse("FREQ=WEEKLY;BYDAY=XX"))
        assertNull(RepeatRule.parse("nonsense"))
        assertNull(RepeatRule.parse(""))
    }

    @Test
    fun daily_withInterval() {
        assertEquals(d("2026-10-27"), next("FREQ=DAILY;INTERVAL=3", "2026-10-24"))
        // Far after the anchor, the grid is still anchored at the start date.
        assertEquals(d("2026-11-02"), next("FREQ=DAILY;INTERVAL=3", "2026-10-24", "2026-10-31"))
    }

    @Test
    fun weekly_defaultsToAnchorWeekday_andByDay() {
        // 2026-10-24 is a Saturday.
        assertEquals(d("2026-10-31"), next("FREQ=WEEKLY", "2026-10-24"))
        assertEquals(d("2026-10-26"), next("FREQ=WEEKLY;BYDAY=MO,WE", "2026-10-24"))
        assertEquals(d("2026-10-28"), next("FREQ=WEEKLY;BYDAY=MO,WE", "2026-10-26"))
    }

    @Test
    fun weekly_everyOtherWeek_staysOnGrid() {
        val rule = RepeatRule.weekly(MONDAY, WEDNESDAY, interval = 2)
        val anchor = d("2026-10-26") // Monday
        assertEquals(d("2026-10-28"), RecurrenceCalculator.next(rule, anchor, anchor))
        assertEquals(d("2026-11-09"), RecurrenceCalculator.next(rule, anchor, d("2026-10-28")))
        // Starting from the advanced anchor gives the same series.
        assertEquals(d("2026-11-09"), RecurrenceCalculator.next(rule, d("2026-10-28"), d("2026-10-28")))
    }

    @Test
    fun monthly_on31st_skipsShortMonths() {
        assertEquals(d("2027-03-31"), next("FREQ=MONTHLY", "2027-01-31"))
        assertEquals(d("2027-05-31"), next("FREQ=MONTHLY", "2027-03-31"))
    }

    @Test
    fun monthly_lastDayOfMonth() {
        assertEquals(d("2027-02-28"), next("FREQ=MONTHLY;BYMONTHDAY=-1", "2027-01-31"))
        assertEquals(d("2028-02-29"), next("FREQ=MONTHLY;BYMONTHDAY=-1", "2028-01-31"))
    }

    @Test
    fun monthly_nthWeekday() {
        // Second Monday: Nov 9 2026; last Friday: Oct 30 2026.
        assertEquals(d("2026-11-09"), next("FREQ=MONTHLY;BYDAY=2MO", "2026-10-24"))
        assertEquals(d("2026-10-30"), next("FREQ=MONTHLY;BYDAY=-1FR", "2026-10-24"))
        assertEquals(d("2026-11-27"), next("FREQ=MONTHLY;BYDAY=-1FR", "2026-10-30"))
    }

    @Test
    fun yearly_leapDay_onlyInLeapYears() {
        assertEquals(d("2032-02-29"), next("FREQ=YEARLY", "2028-02-29"))
        assertEquals(d("2027-02-28"), next("FREQ=YEARLY", "2026-02-28"))
    }

    @Test
    fun yearly_byMonth() {
        assertEquals(d("2027-01-15"), next("FREQ=YEARLY;BYMONTH=1,7;BYMONTHDAY=15", "2026-07-15"))
        assertEquals(d("2027-07-15"), next("FREQ=YEARLY;BYMONTH=1,7;BYMONTHDAY=15", "2027-01-15"))
    }

    @Test
    fun until_isInclusive_andEndsSeries() {
        assertEquals(d("2026-10-26"), next("FREQ=DAILY;UNTIL=20261026", "2026-10-25"))
        assertNull(next("FREQ=DAILY;UNTIL=20261026", "2026-10-26"))
    }

    @Test
    fun impossibleRule_terminates() {
        assertNull(next("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30", "2026-01-01"))
    }

    @Test
    fun occurrences_respectCountFromAnchor_andWindow() {
        val rule = RepeatRule.parse("FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5")!!
        val all = RecurrenceCalculator.occurrences(rule, d("2026-10-26"), d("2026-10-01"), d("2026-12-31"))
        assertEquals(listOf("2026-10-26", "2026-10-28", "2026-11-02", "2026-11-04", "2026-11-09").map(::d), all)
        val window = RecurrenceCalculator.occurrences(rule, d("2026-10-26"), d("2026-11-01"), d("2026-11-03"))
        assertEquals(listOf(d("2026-11-02")), window)
    }

    @Test
    fun occurrences_withoutCount_jumpToWindow() {
        val rule = RepeatRule.daily(interval = 2)
        val window = RecurrenceCalculator.occurrences(rule, d("2026-01-01"), d("2026-12-30"), d("2027-01-04"))
        assertEquals(listOf("2026-12-31", "2027-01-02", "2027-01-04").map(::d), window)
    }

    @Test
    fun nextFromCompletion_addsInterval() {
        assertEquals(d("2026-11-07"), RecurrenceCalculator.nextFromCompletion(RepeatRule.weekly(interval = 2), d("2026-10-24")))
        assertEquals(d("2027-02-28"), RecurrenceCalculator.nextFromCompletion(RepeatRule.monthly(), d("2027-01-31")))
    }
}
