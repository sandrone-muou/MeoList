package com.meolist.domain.calendar

import com.meolist.domain.task.TaskFilter
import com.meolist.domain.task.TaskGrouping
import com.meolist.domain.task.TaskItem
import com.meolist.domain.task.TaskSnapshot
import java.time.LocalDate
import java.time.ZoneId

enum class CalendarPeriod { MONTH, WEEK }

object TaskCalendarRules {
    fun move(date: LocalDate, period: CalendarPeriod, steps: Long): LocalDate = when (period) {
        CalendarPeriod.MONTH -> date.plusMonths(steps)
        CalendarPeriod.WEEK -> date.plusWeeks(steps)
    }

    /** The same visible top-level tasks as All, independently of the list screen's filter. */
    fun byDate(snapshot: TaskSnapshot, today: LocalDate, zone: ZoneId): Map<LocalDate, List<TaskItem>> =
        TaskGrouping.group(
            TaskFilter.All,
            snapshot.copy(tasks = snapshot.tasks.filterNot { it.isDeleted }),
            today,
            zone,
        ).flatMap { it.items }
            .mapNotNull { item -> CalendarRules.taskDay(item.task, zone)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, items) -> items.sortedBy { it.task.isDone } }
}
