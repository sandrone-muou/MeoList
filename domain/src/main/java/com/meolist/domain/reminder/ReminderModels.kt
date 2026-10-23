package com.meolist.domain.reminder

import com.meolist.domain.common.MINUTE_MS
import com.meolist.domain.common.toLocalDate
import com.meolist.domain.common.toMillis
import com.meolist.domain.task.Task
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

enum class ReminderOwner(val value: Int) {
    TASK(0), HABIT(1), COUNTDOWN(2);

    companion object {
        fun fromValue(value: Int): ReminderOwner = entries.firstOrNull { it.value == value } ?: TASK
    }
}

enum class ReminderKind(val value: Int) {
    /** [Reminder.offsetMin] before the owner's time (for all-day owners: whole days before, at [Reminder.minuteOfDay]). */
    RELATIVE(0),
    /** Every day the owner is scheduled, at [Reminder.minuteOfDay]. */
    TIME_OF_DAY(1);

    companion object {
        fun fromValue(value: Int): ReminderKind = entries.firstOrNull { it.value == value } ?: RELATIVE
    }
}

/**
 * A reminder attached to a task, habit or countdown. [nextFireAt] is the computed fire time
 * (null once fired or when there is nothing to fire); [snoozedUntil] overrides it after a snooze.
 */
data class Reminder(
    val id: Long = 0,
    val ownerType: ReminderOwner,
    val ownerId: Long,
    val kind: ReminderKind = ReminderKind.RELATIVE,
    val offsetMin: Int = 0,
    val minuteOfDay: Int? = null,
    val nextFireAt: Long? = null,
    val snoozedUntil: Long? = null,
    val enabled: Boolean = true,
) {
    val effectiveFireAt: Long? get() = if (!enabled) null else snoozedUntil ?: nextFireAt

    /** Identity of the user's choice, ignoring scheduling state. */
    fun sameSetting(other: Reminder): Boolean =
        kind == other.kind && offsetMin == other.offsetMin && minuteOfDay == other.minuteOfDay

    companion object {
        const val DEFAULT_MINUTE_OF_DAY = 9 * 60
        const val DAY_MINUTES = 24 * 60
    }
}

object ReminderTimes {
    /** When a task reminder should fire, or null if the task has no due date. Ignores done/deleted state. */
    fun taskFireAt(task: Task, reminder: Reminder, zone: ZoneId): Long? {
        val due = task.dueAt ?: return null
        if (!task.allDay) return due - reminder.offsetMin * MINUTE_MS
        val minute = reminder.minuteOfDay ?: Reminder.DEFAULT_MINUTE_OF_DAY
        val date = due.toLocalDate(zone).minusDays((reminder.offsetMin / Reminder.DAY_MINUTES).toLong())
        return date.atTime(minute / 60, minute % 60).toMillis(zone)
    }
}

interface ReminderRepository {
    fun observeForOwner(ownerType: ReminderOwner, ownerId: Long): Flow<List<Reminder>>
    /** Ids of owners of [ownerType] that have at least one enabled reminder. */
    fun observeOwnerIds(ownerType: ReminderOwner): Flow<Set<Long>>
    suspend fun getForOwner(ownerType: ReminderOwner, ownerId: Long): List<Reminder>
    suspend fun get(id: Long): Reminder?
    /** Replaces every reminder of the owner with [reminders] (ids are ignored). */
    suspend fun replaceForOwner(ownerType: ReminderOwner, ownerId: Long, reminders: List<Reminder>)
    suspend fun update(reminder: Reminder)
    suspend fun updateAll(reminders: List<Reminder>)
    suspend fun getAll(): List<Reminder>
    /** Enabled reminders whose effective fire time is at or before [at]. */
    suspend fun getDue(at: Long): List<Reminder>
    /** Earliest effective fire time of all enabled reminders. */
    suspend fun nextFireTime(): Long?
    /** Removes reminders whose owner no longer exists. */
    suspend fun deleteOrphans()
}

/** Wakes the app at a given time. Implemented with AlarmManager. */
interface AlarmPort {
    /** Schedules the single reminder alarm at [atMillis], replacing any previous one; null cancels it. */
    fun schedule(atMillis: Long?)
    fun canScheduleExact(): Boolean
}

/** Shows and removes reminder notifications. */
interface ReminderNotifier {
    fun showTaskReminder(task: Task, reminderId: Long)
    fun cancelTaskReminder(taskId: Long)
}
