package com.meolist.domain.testing

import com.meolist.domain.reminder.AlarmPort
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderNotifier
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.reminder.ReminderRepository
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ReminderRepository]; orphans are resolved against [tasks]. */
class FakeReminderRepository(private val tasks: TaskRepository) : ReminderRepository {
    private val rows = MutableStateFlow<Map<Long, Reminder>>(emptyMap())
    private var nextId = 1L

    val all: List<Reminder> get() = rows.value.values.sortedBy { it.id }

    override fun observeForOwner(ownerType: ReminderOwner, ownerId: Long): Flow<List<Reminder>> =
        rows.map { m -> m.values.filter { it.ownerType == ownerType && it.ownerId == ownerId }.sortedBy { it.id } }

    override fun observeOwnerIds(ownerType: ReminderOwner): Flow<Set<Long>> =
        rows.map { m -> m.values.filter { it.ownerType == ownerType && it.enabled }.map { it.ownerId }.toSet() }

    override suspend fun getForOwner(ownerType: ReminderOwner, ownerId: Long): List<Reminder> =
        all.filter { it.ownerType == ownerType && it.ownerId == ownerId }

    override suspend fun get(id: Long): Reminder? = rows.value[id]

    override suspend fun replaceForOwner(ownerType: ReminderOwner, ownerId: Long, reminders: List<Reminder>) {
        val kept = rows.value.filterValues { !(it.ownerType == ownerType && it.ownerId == ownerId) }
        rows.value = kept + reminders.map { r -> val id = nextId++; id to r.copy(id = id) }
    }

    override suspend fun update(reminder: Reminder) {
        if (reminder.id in rows.value) rows.value = rows.value + (reminder.id to reminder)
    }

    override suspend fun updateAll(reminders: List<Reminder>) = reminders.forEach { update(it) }

    override suspend fun getAll(): List<Reminder> = all

    override suspend fun getDue(at: Long): List<Reminder> = all.filter { (it.effectiveFireAt ?: Long.MAX_VALUE) <= at }

    override suspend fun nextFireTime(): Long? = all.mapNotNull { it.effectiveFireAt }.minOrNull()

    override suspend fun deleteOrphans() {
        val doomed = all.filter { it.ownerType == ReminderOwner.TASK && tasks.get(it.ownerId) == null }.map { it.id }.toSet()
        rows.value = rows.value.filterKeys { it !in doomed }
    }
}

class FakeAlarmPort(var exact: Boolean = true) : AlarmPort {
    var scheduledAt: Long? = null
        private set

    override fun schedule(atMillis: Long?) {
        scheduledAt = atMillis
    }

    override fun canScheduleExact(): Boolean = exact
}

class FakeNotifier : ReminderNotifier {
    val shown = mutableListOf<Pair<Long, Long>>()
    val visible = mutableSetOf<Long>()

    override fun showTaskReminder(task: Task, reminderId: Long) {
        shown += task.id to reminderId
        visible += task.id
    }

    override fun cancelTaskReminder(taskId: Long) {
        visible -= taskId
    }
}
