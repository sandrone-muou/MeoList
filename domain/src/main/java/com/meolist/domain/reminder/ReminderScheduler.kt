package com.meolist.domain.reminder

import com.meolist.domain.common.Clock
import com.meolist.domain.common.MINUTE_MS
import com.meolist.domain.task.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps reminder fire times up to date and drives a single alarm chain: only the earliest
 * pending reminder has an alarm; when it fires, everything due is shown and the next one is armed.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    private val reminders: ReminderRepository,
    private val tasks: TaskRepository,
    private val alarm: AlarmPort,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Replaces a task's reminders with [settings] (only kind/offset/minute are used). */
    suspend fun setTaskReminders(taskId: Long, settings: List<Reminder>) = mutex.withLock {
        val distinct = settings.fold(emptyList<Reminder>()) { acc, r -> if (acc.any { it.sameSetting(r) }) acc else acc + r }
        reminders.replaceForOwner(
            ReminderOwner.TASK,
            taskId,
            distinct.map { Reminder(ownerType = ReminderOwner.TASK, ownerId = taskId, kind = it.kind, offsetMin = it.offsetMin, minuteOfDay = it.minuteOfDay) },
        )
        recomputeTask(taskId)
        armNext()
    }

    /** Call after a task (and its subtasks, if [withSubtasks]) changed in any way. */
    suspend fun syncTask(taskId: Long, withSubtasks: Boolean = false) = mutex.withLock {
        recomputeTask(taskId)
        if (withSubtasks) tasks.getSubtasks(taskId).forEach { recomputeTask(it.id) }
        armNext()
    }

    /** Drops reminders of permanently deleted owners. */
    suspend fun purgeOrphans() = mutex.withLock {
        reminders.deleteOrphans()
        armNext()
    }

    /**
     * Re-arms the alarm. With [recompute] every fire time is derived again from its owner, which is
     * needed after a time zone change; reminders whose time has passed are then dropped. Without it,
     * reminders missed while the device was off fire right away.
     */
    suspend fun rescheduleAll(recompute: Boolean = false) = mutex.withLock {
        if (recompute) {
            reminders.getAll().filter { it.ownerType == ReminderOwner.TASK }.map { it.ownerId }.distinct().forEach { recomputeTask(it) }
        }
        armNext()
    }

    /** Alarm callback: shows every due reminder, marks them fired and arms the next one. */
    suspend fun onAlarm() = mutex.withLock {
        val now = clock.now()
        val due = reminders.getDue(now + FIRE_SLACK_MS)
        val fired = due.map { reminder ->
            when (reminder.ownerType) {
                ReminderOwner.TASK -> {
                    val task = tasks.get(reminder.ownerId)
                    if (task != null && !task.isDone && !task.isDeleted) notifier.showTaskReminder(task, reminder.id)
                }
                // Habit and countdown reminders arrive with their features.
                ReminderOwner.HABIT, ReminderOwner.COUNTDOWN -> Unit
            }
            reminder.copy(nextFireAt = null, snoozedUntil = null)
        }
        if (fired.isNotEmpty()) reminders.updateAll(fired)
        armNext()
    }

    /** Shows the reminder again [minutes] from now. */
    suspend fun snooze(reminderId: Long, minutes: Int = DEFAULT_SNOOZE_MINUTES) = mutex.withLock {
        val reminder = reminders.get(reminderId) ?: return@withLock
        reminders.update(reminder.copy(snoozedUntil = clock.now() + minutes * MINUTE_MS))
        if (reminder.ownerType == ReminderOwner.TASK) notifier.cancelTaskReminder(reminder.ownerId)
        armNext()
    }

    private suspend fun recomputeTask(taskId: Long) {
        val own = reminders.getForOwner(ReminderOwner.TASK, taskId)
        val task = tasks.get(taskId)
        val active = task != null && !task.isDone && !task.isDeleted
        if (!active) notifier.cancelTaskReminder(taskId)
        if (own.isEmpty()) return
        val now = clock.now()
        val zone = clock.zone()
        val updated = own.map { r ->
            if (!active) {
                r.copy(nextFireAt = null, snoozedUntil = null)
            } else {
                // A time that has already passed was either shown or deliberately moved into the past.
                val next = ReminderTimes.taskFireAt(task!!, r, zone)?.takeIf { it > now }
                // A new upcoming time means the schedule moved, which supersedes an earlier snooze.
                val snooze = if (next != null && next != r.nextFireAt) null else r.snoozedUntil
                r.copy(nextFireAt = next, snoozedUntil = snooze)
            }
        }.filterIndexed { i, r -> r != own[i] }
        if (updated.isNotEmpty()) reminders.updateAll(updated)
    }

    private suspend fun armNext() {
        alarm.schedule(reminders.nextFireTime())
    }

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        /** Alarms are never early, but reminders a few seconds apart are shown together. */
        private const val FIRE_SLACK_MS = 5_000L
    }
}
