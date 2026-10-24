package com.meolist.domain.habit

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Streaks and completion figures for habits. Pure functions over check-ins. */
object HabitRules {
    fun isScheduled(habit: Habit, day: LocalDate): Boolean = when (val schedule = habit.schedule) {
        HabitSchedule.Daily -> true
        is HabitSchedule.Weekdays -> day.dayOfWeek in schedule.days
        is HabitSchedule.WeeklyTimes -> true
    }

    fun isDone(habit: Habit, value: Int): Boolean = value >= habit.dailyTarget.coerceAtLeast(1)

    /** Value a single tap writes: the whole daily target. */
    fun tapValue(habit: Habit): Int = habit.dailyTarget.coerceAtLeast(1)

    /** Consecutive scheduled days (or weeks for [HabitSchedule.WeeklyTimes]) done up to today. */
    fun currentStreak(
        habit: Habit,
        checkIns: List<HabitCheckIn>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): Int = when (habit.schedule) {
        is HabitSchedule.WeeklyTimes -> weeklyStreak(habit, checkIns, today, firstDayOfWeek, countCurrent = true)
        else -> dailyStreak(habit, checkIns, today)
    }

    fun bestStreak(
        habit: Habit,
        checkIns: List<HabitCheckIn>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): Int = when (habit.schedule) {
        is HabitSchedule.WeeklyTimes -> weeklyBest(habit, checkIns, today, firstDayOfWeek)
        else -> dailyBest(habit, checkIns, today)
    }

    /** Done and scheduled days between [from] and [to] (inclusive), used for completion rates. */
    fun completion(habit: Habit, checkIns: List<HabitCheckIn>, from: LocalDate, to: LocalDate): Pair<Int, Int> {
        val byDay = checkIns.filter { it.habitId == habit.id }.associateBy { it.day }
        var scheduled = 0
        var done = 0
        var day = from
        while (day <= to) {
            if (isScheduled(habit, day)) {
                scheduled++
                if (isDone(habit, byDay[day]?.value ?: 0)) done++
            }
            day = day.plusDays(1)
        }
        return done to scheduled
    }

    /** Number of check-ins that reached the target, all time. */
    fun totalDone(habit: Habit, checkIns: List<HabitCheckIn>): Int =
        checkIns.count { it.habitId == habit.id && isDone(habit, it.value) }

    private fun dailyStreak(habit: Habit, checkIns: List<HabitCheckIn>, today: LocalDate): Int {
        val done = doneDays(habit, checkIns)
        var day = today
        var streak = 0
        var guard = 0
        while (guard < 3660) {
            guard++
            if (!isScheduled(habit, day)) {
                day = day.minusDays(1)
                continue
            }
            when {
                day in done -> streak++
                // Today can still be completed; it does not break the streak yet.
                day == today -> Unit
                else -> break
            }
            day = day.minusDays(1)
        }
        return streak
    }

    private fun dailyBest(habit: Habit, checkIns: List<HabitCheckIn>, today: LocalDate): Int {
        val done = doneDays(habit, checkIns)
        val start = done.minOrNull() ?: return 0
        var day = start
        var run = 0
        var best = 0
        while (day <= today) {
            if (isScheduled(habit, day)) {
                if (day in done) {
                    run++
                    best = maxOf(best, run)
                } else if (day != today) {
                    run = 0
                }
            }
            day = day.plusDays(1)
        }
        return best
    }

    private fun weeklyStreak(
        habit: Habit,
        checkIns: List<HabitCheckIn>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        countCurrent: Boolean,
    ): Int {
        val target = (habit.schedule as HabitSchedule.WeeklyTimes).times
        val perWeek = weeklyDone(habit, checkIns, firstDayOfWeek)
        var week = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val currentWeek = week
        var streak = 0
        var guard = 0
        while (guard < 520) {
            guard++
            val done = perWeek[week] ?: 0
            when {
                done >= target -> streak++
                // This week can still reach its target, so it neither counts nor breaks the streak.
                week == currentWeek && countCurrent -> Unit
                else -> break
            }
            week = week.minusWeeks(1)
        }
        return streak
    }

    private fun weeklyBest(
        habit: Habit,
        checkIns: List<HabitCheckIn>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Int {
        val target = (habit.schedule as HabitSchedule.WeeklyTimes).times
        val perWeek = weeklyDone(habit, checkIns, firstDayOfWeek)
        if (perWeek.isEmpty()) return 0
        var week = perWeek.keys.min()
        val currentWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        var run = 0
        var best = 0
        while (week <= currentWeek) {
            if ((perWeek[week] ?: 0) >= target) {
                run++
                best = maxOf(best, run)
            } else if (week != currentWeek) {
                run = 0
            }
            week = week.plusWeeks(1)
        }
        return best
    }

    private fun doneDays(habit: Habit, checkIns: List<HabitCheckIn>): Set<LocalDate> =
        checkIns.filter { it.habitId == habit.id && isDone(habit, it.value) }.mapTo(HashSet()) { it.day }

    private fun weeklyDone(
        habit: Habit,
        checkIns: List<HabitCheckIn>,
        firstDayOfWeek: DayOfWeek,
    ): Map<LocalDate, Int> = checkIns
        .filter { it.habitId == habit.id && isDone(habit, it.value) }
        .groupingBy { it.day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)) }
        .eachCount()

    /** Days in the schedule between two dates, for summaries such as "3 of 5 this week". */
    fun scheduledDays(habit: Habit, from: LocalDate, to: LocalDate): Int {
        var count = 0
        var day = from
        while (day <= to) {
            if (isScheduled(habit, day)) count++
            day = day.plusDays(1)
        }
        return count
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)
}
