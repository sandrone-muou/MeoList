package com.meolist.domain.calendar

import com.meolist.domain.countdown.Countdown
import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitCheckIn
import com.meolist.domain.habit.HabitSchedule
import com.meolist.domain.task.Task
import com.meolist.domain.tracker.TimeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun at(day: LocalDate, h: Int, m: Int = 0): Long =
        LocalDateTime.of(day, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun monthGridCoversSixWeeksStartingOnTheConfiguredDay() {
        val june = CalendarRules.monthGrid(YearMonth.of(2026, 6), DayOfWeek.MONDAY)
        assertEquals(42, june.size)
        assertEquals(LocalDate.of(2026, 6, 1), june.first()) // June 2026 starts on a Monday
        val october = CalendarRules.monthGrid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 9, 28), october.first())
        assertEquals(LocalDate.of(2026, 11, 8), october.last())
        // A Sunday start shifts the grid.
        val sunday = CalendarRules.monthGrid(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 9, 27), sunday.first())
    }

    @Test
    fun weekDaysAreSevenConsecutiveDays() {
        val week = CalendarRules.weekDays(LocalDate.of(2026, 10, 24), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 19), week.first())
        assertEquals(LocalDate.of(2026, 10, 25), week.last())
        assertEquals(7, week.size)
    }

    @Test
    fun taskDayUsesTheLocalDate() {
        val task = Task(id = 1, title = "T", dueAt = at(LocalDate.of(2026, 10, 24), 23, 30))
        assertEquals(LocalDate.of(2026, 10, 24), CalendarRules.taskDay(task, zone))
        assertEquals(null, CalendarRules.taskDay(Task(title = "No date"), zone))
    }

    @Test
    fun countdownOccursOnItsDayAndAnniversary() {
        val birthday = Countdown(id = 1, title = "B", baseDay = LocalDate.of(1990, 3, 2), yearly = true)
        assertTrue(CalendarRules.countdownOccursOn(birthday, LocalDate.of(2026, 3, 2)))
        assertTrue(CalendarRules.countdownOccursOn(birthday, LocalDate.of(1990, 3, 2)))
        assertFalse(CalendarRules.countdownOccursOn(birthday, LocalDate.of(2026, 3, 3)))
        val trip = Countdown(id = 2, title = "T", baseDay = LocalDate.of(2026, 11, 23))
        assertTrue(CalendarRules.countdownOccursOn(trip, LocalDate.of(2026, 11, 23)))
        assertFalse(CalendarRules.countdownOccursOn(trip, LocalDate.of(2027, 11, 23)))

        val leap = Countdown(id = 3, title = "L", baseDay = LocalDate.of(2000, 2, 29), yearly = true)
        assertTrue(CalendarRules.countdownOccursOn(leap, LocalDate.of(2027, 2, 28)))
        assertFalse(CalendarRules.countdownOccursOn(leap, LocalDate.of(2028, 2, 28)))
    }

    @Test
    fun buildMapsEverythingOntoTheRightDays() {
        val mon = LocalDate.of(2026, 10, 19)
        val days = CalendarRules.weekDays(mon, DayOfWeek.MONDAY)
        val readings = Habit(id = 1, name = "Read", schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.TUESDAY)), startDay = mon)
        val daily = Habit(id = 2, name = "Water", startDay = mon)
        val archived = Habit(id = 3, name = "Old", archived = true, startDay = mon)
        val tasks = listOf(
            Task(id = 1, title = "Due Monday", dueAt = at(mon, 9)),
            Task(id = 2, title = "No date"),
        )
        val checks = listOf(HabitCheckIn(1, mon.plusDays(1), 1), HabitCheckIn(2, mon, 1))
        val countdown = Countdown(id = 1, title = "Trip", baseDay = mon.plusDays(3))
        val records = listOf(
            TimeRecord(id = 1, typeId = 1, startAt = at(mon, 22), endAt = at(mon.plusDays(1), 2)),
        )

        val calendar = CalendarRules.build(days, tasks, listOf(readings, daily, archived), checks, listOf(countdown), records, zone)

        assertEquals(7, calendar.size)
        assertEquals(listOf("Due Monday"), calendar[mon]!!.tasks.map { it.title })
        assertEquals(listOf(2L), calendar[mon]!!.habits.map { it.id })
        assertTrue(calendar[mon]!!.doneHabitIds.contains(2L))
        assertEquals(listOf(1L, 2L), calendar[mon.plusDays(1)]!!.habits.map { it.id })
        assertTrue(calendar[mon.plusDays(1)]!!.doneHabitIds.contains(1L))
        assertEquals(listOf("Trip"), calendar[mon.plusDays(3)]!!.countdowns.map { it.title })
        // The record 22:00-02:00 counts two hours on Monday and two on Tuesday.
        assertEquals(2 * hour, calendar[mon]!!.trackedMs)
        assertEquals(2 * hour, calendar[mon.plusDays(1)]!!.trackedMs)
        assertEquals(1, calendar[mon]!!.records.size)
        assertEquals(1, calendar[mon.plusDays(1)]!!.records.size)
        val idle = calendar[mon.plusDays(2)]!!
        assertTrue(idle.tasks.isEmpty() && idle.records.isEmpty() && idle.countdowns.isEmpty())
        assertFalse(calendar[mon]!!.isEmpty)
    }
}
