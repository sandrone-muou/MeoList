package com.meolist.domain.task

import com.meolist.domain.common.Clock
import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.toMillis
import com.meolist.domain.common.today
import com.meolist.domain.farm.FarmHook
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.repeat.RecurrenceCalculator
import com.meolist.domain.repeat.RepeatRule
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Completing a repeating task: the finished occurrence is kept as a done copy ([copyId]) and the
 * task itself moves to its next date. Holds what [TaskInteractor.undoRepeat] needs to revert it.
 */
data class RepeatAdvance(
    val taskId: Long,
    val copyId: Long,
    val previous: Task,
    val previousSubtasks: List<Task>,
    val nextDueAt: Long?,
)

/**
 * Write operations on tasks. Screens call this instead of the repository so that
 * follow-up work (repeat instances, reminders, farm nutrients) has one entry point.
 */
class TaskInteractor @Inject constructor(
    private val repo: TaskRepository,
    private val clock: Clock,
    private val reminders: ReminderScheduler,
    private val completionHook: TaskCompletionHook = TaskCompletionHook.None,
    private val farm: FarmHook = FarmHook.None,
) {
    suspend fun add(
        title: String,
        listId: Long? = null,
        parentId: Long? = null,
        dueAt: Long? = null,
        allDay: Boolean = true,
        priority: Priority = Priority.NONE,
        tagIds: Set<Long> = emptySet(),
    ): Long {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "title must not be blank" }
        val now = clock.now()
        val parent = parentId?.let { repo.get(it) }
        val id = repo.insert(
            Task(
                listId = parent?.listId ?: listId,
                parentId = parentId,
                title = clean,
                dueAt = dueAt,
                allDay = allDay,
                priority = priority,
                sortOrder = repo.nextSortOrder(parent?.listId ?: listId),
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (tagIds.isNotEmpty()) repo.setTags(id, tagIds)
        return id
    }

    /**
     * Saves the user-editable fields of [edited]. Status, completion and trash state are owned by
     * dedicated operations, so a stale copy from a screen can never undo them.
     */
    suspend fun save(edited: Task) {
        val stored = repo.get(edited.id) ?: return
        val rule = RepeatRule.parse(edited.repeatRule)
        val merged = stored.copy(
            title = edited.title.trim().ifEmpty { stored.title },
            note = edited.note,
            priority = edited.priority,
            startAt = edited.startAt,
            dueAt = edited.dueAt,
            allDay = edited.allDay,
            listId = if (stored.parentId == null) edited.listId else stored.listId,
            repeatRule = rule?.toRRule(),
            repeatFromCompletion = rule != null && edited.repeatFromCompletion,
        )
        if (merged == stored) return
        val now = clock.now()
        repo.update(merged.copy(updatedAt = now))
        // Subtasks always follow their parent's list.
        if (stored.parentId == null && merged.listId != stored.listId) {
            repo.getSubtasks(stored.id).forEach { repo.update(it.copy(listId = merged.listId, updatedAt = now)) }
        }
        if (merged.dueAt != stored.dueAt || merged.allDay != stored.allDay) reminders.syncTask(stored.id)
        if (merged.priority != stored.priority && merged.isDone) farm.onNutrientsMayHaveChanged()
    }

    /**
     * Marks a task done or not done. Completing a repeating task that has another occurrence
     * advances it instead and returns how to undo that; otherwise returns null.
     */
    suspend fun setDone(id: Long, done: Boolean): RepeatAdvance? {
        val task = repo.get(id) ?: return null
        if (task.isDone == done) return null
        val now = clock.now()
        if (done) {
            nextOccurrence(task)?.let { next ->
                val advance = advance(task, next, now)
                completionHook.onDoneChanged(id, true)
                farm.onNutrientsMayHaveChanged()
                return advance
            }
        }
        repo.update(
            task.copy(
                status = if (done) TaskStatus.DONE else TaskStatus.TODO,
                completedAt = if (done) now else null,
                updatedAt = now,
            ),
        )
        reminders.syncTask(id)
        completionHook.onDoneChanged(id, done)
        farm.onNutrientsMayHaveChanged()
        return null
    }

    /** Reverts [setDone] on a repeating task: removes the done copy and puts the task back. */
    suspend fun undoRepeat(advance: RepeatAdvance) {
        repo.deletePermanently(advance.copyId)
        if (repo.get(advance.taskId) == null) return
        repo.update(advance.previous.copy(updatedAt = clock.now()))
        advance.previousSubtasks.forEach { sub -> if (repo.get(sub.id) != null) repo.update(sub) }
        reminders.syncTask(advance.taskId)
        farm.onNutrientsMayHaveChanged()
    }

    suspend fun setReminders(taskId: Long, settings: List<Reminder>) = reminders.setTaskReminders(taskId, settings)

    suspend fun moveToTrash(id: Long) {
        repo.moveToTrash(id, clock.now())
        reminders.syncTask(id, withSubtasks = true)
        farm.onNutrientsMayHaveChanged()
    }

    suspend fun restore(id: Long) {
        val task = repo.get(id) ?: return
        // Restoring a subtask whose parent is still in the trash restores the parent too.
        task.parentId?.let { parentId ->
            repo.get(parentId)?.takeIf { it.isDeleted }?.let {
                repo.restore(it.id)
                reminders.syncTask(it.id, withSubtasks = true)
            }
        }
        repo.restore(id)
        reminders.syncTask(id, withSubtasks = true)
        farm.onNutrientsMayHaveChanged()
    }

    suspend fun deletePermanently(id: Long) {
        repo.deletePermanently(id)
        reminders.purgeOrphans()
        farm.onNutrientsMayHaveChanged()
    }

    suspend fun emptyTrash() {
        repo.emptyTrash()
        reminders.purgeOrphans()
        farm.onNutrientsMayHaveChanged()
    }

    suspend fun setTags(taskId: Long, tagIds: Set<Long>) = repo.setTags(taskId, tagIds)

    /** The next due time (and remaining rule) of a repeating task, or null when the series ends here. */
    private fun nextOccurrence(task: Task): Pair<Long, RepeatRule>? {
        val rule = RepeatRule.parse(task.repeatRule) ?: return null
        if (rule.count != null && rule.count <= 1) return null
        val zone = clock.zone()
        val today = clock.today()
        val due = task.dueAt?.toLocalDateTime(zone)
        val baseDate = due?.toLocalDate() ?: today
        val nextDate = if (task.repeatFromCompletion) {
            RecurrenceCalculator.nextFromCompletion(rule, today).takeIf { rule.until == null || it <= rule.until }
        } else {
            // Overdue occurrences are skipped: the next one is after today.
            RecurrenceCalculator.next(rule, anchor = baseDate, after = maxOf(baseDate, today))
        } ?: return null
        val nextDue = if (due == null || task.allDay) nextDate.startOfDayMillis(zone)
        else nextDate.atTime(due.toLocalTime()).toMillis(zone)
        return nextDue to rule.copy(count = rule.count?.minus(1))
    }

    private suspend fun advance(task: Task, next: Pair<Long, RepeatRule>, now: Long): RepeatAdvance {
        val (nextDue, nextRule) = next
        val zone = clock.zone()
        val copyId = repo.insert(
            task.copy(
                id = 0,
                status = TaskStatus.DONE,
                completedAt = now,
                repeatRule = null,
                repeatFromCompletion = false,
                repeatOriginId = task.id,
                createdAt = now,
                updatedAt = now,
            ),
        )
        repo.getTagIds(task.id).takeIf { it.isNotEmpty() }?.let { repo.setTags(copyId, it) }

        val oldDate = (task.dueAt ?: now).toLocalDateTime(zone).toLocalDate()
        val shiftDays = ChronoUnit.DAYS.between(oldDate, nextDue.toLocalDateTime(zone).toLocalDate())
        repo.update(
            task.copy(
                dueAt = nextDue,
                allDay = task.dueAt == null || task.allDay,
                // Keeps the wall-clock start time, so a DST change does not shift it by an hour.
                startAt = task.startAt?.let { it.toLocalDateTime(zone).plusDays(shiftDays).toMillis(zone) },
                repeatRule = nextRule.toRRule(),
                updatedAt = now,
            ),
        )
        val subtasks = repo.getSubtasks(task.id)
        subtasks.filter { it.isDone && !it.isDeleted }.forEach {
            repo.update(it.copy(status = TaskStatus.TODO, completedAt = null, updatedAt = now))
        }
        reminders.syncTask(task.id)
        return RepeatAdvance(task.id, copyId, task, subtasks, nextDue)
    }
}
