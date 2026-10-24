package com.meolist.domain.habit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitRulesTest {
    private val today = LocalDate.of(2026, 10, 24) // Saturday

    private fun habit(
        schedule: HabitSchedule = HabitSchedule.Daily,
        target: Int = 1,
        start: LocalDate = today.minusDays(60),
    ) = Habit(id = 1, name = "Read", schedule = schedule, dailyTarget = target, startDay = start)

    private fun checks(vararg days: LocalDate) = days.map { HabitCheckIn(1, it, 1) }

    @Test
    fun scheduleEncodingRoundTrips() {
        val schedules = listOf(
            HabitSchedule.Daily,
            HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            HabitSchedule.WeeklyTimes(3),
        )
        schedules.forEach { assertEquals(it, HabitSchedule.decode(it.encode())) }
        assertEquals(HabitSchedule.Daily, HabitSchedule.decode(""))
        assertEquals(HabitSchedule.Daily, HabitSchedule.decode("days:"))
        assertEquals(HabitSchedule.WeeklyTimes(7), HabitSchedule.decode("weekly:9"))
    }

    @Test
    fun dailyStreak_countsBackAndIgnoresIncompleteToday() {
        val checks = checks(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        // Today is still open: 2 days (yesterday and the day before), then a gap breaks it.
        assertEquals(2, HabitRules.currentStreak(habit(), checks, today))
        // Once today is done it adds up, and the gap still stops the count.
        assertEquals(3, HabitRules.currentStreak(habit(), checks + HabitCheckIn(1, today, 1), today))
    }

    @Test
    fun weekdayHabit_skipsDaysThatAreNotScheduled() {
        // Mon, Wed, Fri only. Today is Saturday, last done Friday.
        val schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val friday = today.minusDays(1)
        val wednesday = today.minusDays(3)
        val monday = today.minusDays(5)
        val checks = checks(friday, wednesday, monday)
        assertEquals(3, HabitRules.currentStreak(habit(schedule), checks, today))
        // A missed Wednesday breaks the chain at Friday.
        assertEquals(1, HabitRules.currentStreak(habit(schedule), checks.filter { it.day != wednesday }, today))
    }

    @Test
    fun countedHabit_needsTheTarget() {
        val h = habit(target = 8)
        val partial = listOf(HabitCheckIn(1, today.minusDays(1), 5), HabitCheckIn(1, today, 8))
        assertFalse(HabitRules.isDone(h, 5))
        assertTrue(HabitRules.isDone(h, 8))
        // Yesterday was partial: only today counts.
        assertEquals(1, HabitRules.currentStreak(h, partial, today))
    }

    @Test
    fun bestStreak_findsTheLongestRun() {
        val checks = checks(
            today.minusDays(10), today.minusDays(9), today.minusDays(8),
            today.minusDays(2), today.minusDays(1), today,
        )
        assertEquals(3, HabitRules.bestStreak(habit(), checks, today))
    }

    @Test
    fun weeklyTimesStreak_countsWeeksAndWaitsForCurrentWeek() {
        // Weekly Mon-start target of 2. This week (Mon Oct 19) has one check-in so far.
        val h = habit(HabitSchedule.WeeklyTimes(2))
        val checks = checks(
            today.minusDays(0), // Sat this week
            today.minusDays(7), today.minusDays(6), // week of Oct 12
            today.minusDays(14), today.minusDays(15), // week of Oct 5
        )
        assertEquals(2, HabitRules.currentStreak(h, checks, today, DayOfWeek.MONDAY))
        assertEquals(2, HabitRules.bestStreak(h, checks, today, DayOfWeek.MONDAY))
        // Reaching the target this week makes it three.
        val more = checks + HabitCheckIn(1, today.minusDays(1), 1)
        assertEquals(3, HabitRules.currentStreak(h, more, today, DayOfWeek.MONDAY))
    }

    @Test
    fun completionCountsScheduledDaysOnly() {
        val schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val h = habit(schedule, start = LocalDate.of(2026, 10, 12))
        val checks = checks(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16)) // Mon, Fri
        val (done, scheduled) = HabitRules.completion(h, checks, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 24))
        assertEquals(2, done)
        assertEquals(6, scheduled) // Mon/Wed/Fri over two weeks
        assertEquals(2, HabitRules.totalDone(h, checks))
    }

    @Test
    fun streakIncludesCheckInsFilledInBeforeTheStartDay() {
        // Habits created today may still be filled in for earlier days.
        val h = habit(start = today)
        val checks = checks(today.minusDays(2), today.minusDays(1), today)
        assertEquals(3, HabitRules.currentStreak(h, checks, today))
        assertEquals(3, HabitRules.bestStreak(h, checks, today))
    }
}
