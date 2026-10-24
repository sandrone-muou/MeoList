package com.meolist.domain.task

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Eisenhower quadrants. Importance is the priority; urgency comes from the due date. */
enum class Quadrant {
    URGENT_IMPORTANT,
    NOT_URGENT_IMPORTANT,
    URGENT_NOT_IMPORTANT,
    NOT_URGENT_NOT_IMPORTANT;

    val isImportant: Boolean get() = this == URGENT_IMPORTANT || this == NOT_URGENT_IMPORTANT
}

object QuadrantRules {
    /** Medium and high priorities count as important. */
    fun isImportant(task: Task): Boolean = task.priority == Priority.MEDIUM || task.priority == Priority.HIGH

    /** Due within [urgentDays] days (already overdue included). Tasks without a due date are not urgent. */
    fun isUrgent(task: Task, today: LocalDate, urgentDays: Int, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return dueDay <= today.plusDays(urgentDays.toLong())
    }

    fun of(task: Task, today: LocalDate, urgentDays: Int, zone: ZoneId): Quadrant = when {
        isImportant(task) && isUrgent(task, today, urgentDays, zone) -> Quadrant.URGENT_IMPORTANT
        isImportant(task) -> Quadrant.NOT_URGENT_IMPORTANT
        isUrgent(task, today, urgentDays, zone) -> Quadrant.URGENT_NOT_IMPORTANT
        else -> Quadrant.NOT_URGENT_NOT_IMPORTANT
    }

    /**
     * The priority a task gets when dropped on [quadrant]. Dates are untouched, so urgency keeps
     * following the due date; only importance changes.
     */
    fun priorityFor(quadrant: Quadrant): Priority = when (quadrant) {
        Quadrant.URGENT_IMPORTANT -> Priority.HIGH
        Quadrant.NOT_URGENT_IMPORTANT -> Priority.MEDIUM
        Quadrant.URGENT_NOT_IMPORTANT, Quadrant.NOT_URGENT_NOT_IMPORTANT -> Priority.NONE
    }
}
