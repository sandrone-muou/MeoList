package com.meolist.domain.calendar

import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.countdown.Countdown
import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitCheckIn
import com.meolist.domain.habit.HabitRules
import com.meolist.domain.task.Task
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TrackerTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** What happens on one calendar day. */
data class CalendarDay(
    val date: LocalDate,
    val tasks: List<Task> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val doneHabitIds: Set<Long> = emptySet(),
    val countdowns: List<Countdown> = emptyList(),
    val records: List<TimeRecord> = emptyList(),
    val trackedMs: Long = 0,
) {
    val taskCount: Int get() = tasks.size
    val isEmpty: Boolean get() = tasks.isEmpty() && habits.isEmpty() && countdowns.isEmpty() && records.isEmpty()
}

/** Maps tasks, habits, countdowns and records onto the days of a month or week. */
object CalendarRules {
    /** The 42 days (six weeks) shown for [month], starting on [firstDayOfWeek]. */
    fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }

    fun weekDays(day: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
        val start = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return (0 until 7).map { start.plusDays(it.toLong()) }
    }

    /** The day a task is due; timed tasks use the local date of their due moment. */
    fun taskDay(task: Task, zone: ZoneId): LocalDate? =
        task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    /** Whether a yearly or one-off countdown lands on [day]. */
    fun countdownOccursOn(countdown: Countdown, day: LocalDate): Boolean {
        if (countdown.baseDay == day) return true
        if (!countdown.yearly) return false
        val anniversary = try {
            countdown.baseDay.withYear(day.year)
        } catch (_: java.time.DateTimeException) {
            LocalDate.of(day.year, 2, 28)
        }
        return anniversary == day
    }

    /**
     * Builds the days of a grid or week. [records] must already cover the range; [now] caps the
     * running timers that add to today's tracked time.
     */
    fun build(
        days: List<LocalDate>,
        tasks: List<Task>,
        habits: List<Habit>,
        checkIns: List<HabitCheckIn>,
        countdowns: List<Countdown>,
        records: List<TimeRecord>,
        zone: ZoneId,
    ): Map<LocalDate, CalendarDay> {
        val tasksByDay = tasks
            .filter { !it.isDeleted }
            .mapNotNull { task -> taskDay(task, zone)?.let { it to task } }
            .groupBy({ it.first }, { it.second })
        val checksByDay = checkIns.groupBy { it.day }
        val result = LinkedHashMap<LocalDate, CalendarDay>(days.size)
        for (day in days) {
            val dayStart = day.startOfDayMillis(zone)
            val dayEnd = day.plusDays(1).startOfDayMillis(zone)
            val dayRecords = records.filter { it.startAt < dayEnd && it.endAt > dayStart }.sortedBy { it.startAt }
            val dayHabits = habits.filter { !it.archived && it.startDay <= day && HabitRules.isScheduled(it, day) }
            result[day] = CalendarDay(
                date = day,
                tasks = tasksByDay[day].orEmpty().sortedWith(compareBy({ it.isDone }, { it.sortOrder })),
                habits = dayHabits,
                doneHabitIds = checksByDay[day].orEmpty()
                    .filter { c -> dayHabits.any { it.id == c.habitId && HabitRules.isDone(it, c.value) } }
                    .mapTo(HashSet()) { it.habitId },
                countdowns = countdowns.filter { !it.archived && countdownOccursOn(it, day) },
                records = dayRecords,
                trackedMs = TrackerTime.totalMs(dayRecords, emptyList(), dayStart, dayEnd, dayStart),
            )
        }
        return result
    }
}
