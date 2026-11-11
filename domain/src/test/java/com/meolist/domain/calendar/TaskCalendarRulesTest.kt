package com.meolist.domain.calendar

import com.meolist.domain.task.Priority
import com.meolist.domain.task.Tag
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskList
import com.meolist.domain.task.TaskSnapshot
import com.meolist.domain.task.TaskStatus
import com.meolist.domain.task.TaskTagRef
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCalendarRulesTest {
    private val date = LocalDate.of(2026, 11, 15)
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(date: LocalDate, time: LocalTime = LocalTime.MIDNIGHT): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun calendarContainsAllVisibleListsAndInboxButNotArchivedDeletedOrSubtasks() {
        val tasks = listOf(
            Task(id = 1, title = "Inbox", dueAt = at(date)),
            Task(id = 2, title = "Work", listId = 1, dueAt = at(date)),
            Task(id = 3, title = "Personal", listId = 2, dueAt = at(date)),
            Task(id = 4, title = "Archived", listId = 3, dueAt = at(date)),
            Task(id = 5, title = "Deleted", deletedAt = 1, dueAt = at(date)),
            Task(id = 6, title = "Subtask", parentId = 2, dueAt = at(date)),
            Task(id = 7, title = "No date"),
        )
        val lists = listOf(
            TaskList(id = 1, name = "Work", color = 0),
            TaskList(id = 2, name = "Personal", color = 0),
            TaskList(id = 3, name = "Archive", color = 0, archived = true),
        )
        val result = TaskCalendarRules.byDate(TaskSnapshot(tasks, lists, emptyList(), emptyList()), date, zone)
        assertEquals(setOf(1L, 2L, 3L), result.getValue(date).map { it.task.id }.toSet())
        assertEquals(1, result.getValue(date).first { it.task.id == 2L }.subtaskTotal)
    }

    @Test
    fun openTasksKeepPriorityOrderAndCompletedTasksComeLastWithDecorations() {
        val tasks = listOf(
            Task(id = 1, title = "Done", dueAt = at(date), status = TaskStatus.DONE, priority = Priority.HIGH),
            Task(id = 2, title = "Low", dueAt = at(date), priority = Priority.LOW),
            Task(id = 3, title = "High", dueAt = at(date), priority = Priority.HIGH),
        )
        val tag = Tag(id = 4, name = "Long tag", color = 0)
        val result = TaskCalendarRules.byDate(
            TaskSnapshot(tasks, emptyList(), listOf(tag), listOf(TaskTagRef(3, 4))), date, zone,
        ).getValue(date)
        assertEquals(listOf(3L, 2L, 1L), result.map { it.task.id })
        assertEquals(listOf(tag), result.first().tags)
    }

    @Test
    fun datesUseInjectedZoneAndIncludeAllDayAndTimedTasks() {
        val tasks = listOf(
            Task(id = 1, title = "All day", dueAt = at(date)),
            Task(id = 2, title = "After midnight", dueAt = at(date, LocalTime.of(0, 30)), allDay = false),
            Task(id = 3, title = "Late night", dueAt = at(date, LocalTime.of(23, 30)), allDay = false),
        )
        val snapshot = TaskSnapshot(tasks, emptyList(), emptyList(), emptyList())
        assertEquals(3, TaskCalendarRules.byDate(snapshot, date, zone).getValue(date).size)
        val utc = TaskCalendarRules.byDate(snapshot, date, ZoneId.of("UTC"))
        assertEquals(setOf(1L, 2L), utc.getValue(date.minusDays(1)).map { it.task.id }.toSet())
        assertEquals(3L, utc.getValue(date).single().task.id)
    }

    @Test
    fun emptySnapshotHasNoDates() {
        assertTrue(TaskCalendarRules.byDate(TaskSnapshot(emptyList(), emptyList(), emptyList(), emptyList()), date, zone).isEmpty())
    }

    @Test
    fun movingFromMonthEndClampsToValidDayIncludingLeapYears() {
        assertEquals(LocalDate.of(2026, 2, 28), TaskCalendarRules.move(LocalDate.of(2026, 1, 31), CalendarPeriod.MONTH, 1))
        assertEquals(LocalDate.of(2028, 2, 29), TaskCalendarRules.move(LocalDate.of(2028, 1, 31), CalendarPeriod.MONTH, 1))
        assertEquals(LocalDate.of(2025, 12, 31), TaskCalendarRules.move(LocalDate.of(2026, 1, 31), CalendarPeriod.MONTH, -1))
    }

    @Test
    fun weekMovementCrossesYearsAndHonoursFirstDay() {
        val next = TaskCalendarRules.move(LocalDate.of(2026, 12, 28), CalendarPeriod.WEEK, 1)
        assertEquals(LocalDate.of(2027, 1, 4), next)
        assertEquals(LocalDate.of(2027, 1, 3), CalendarRules.weekDays(next, DayOfWeek.SUNDAY).first())
        assertEquals(next, CalendarRules.weekDays(next, DayOfWeek.MONDAY).first())
    }

    @Test
    fun leapDayAndAdjacentMonthDatesArePresentInGrid() {
        val days = CalendarRules.monthGrid(YearMonth.of(2028, 2), DayOfWeek.SUNDAY)
        assertEquals(42, days.size)
        assertEquals(DayOfWeek.SUNDAY, days.first().dayOfWeek)
        assertTrue(LocalDate.of(2028, 2, 29) in days)
        assertTrue(days.first().monthValue == 1 && days.last().monthValue == 3)
    }
}
