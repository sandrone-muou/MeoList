package com.meolist.domain.task

import com.meolist.domain.common.Clock
import com.meolist.domain.common.TransactionRunner
import com.meolist.domain.common.toMillis
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TaskDraft(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val priority: Priority = Priority.NONE,
    val listId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val reminder: Reminder? = null,
)

/** Shares atomic task/reminder creation between quick add and the calendar entry point. */
class TaskCreator @Inject constructor(
    private val tasks: TaskInteractor,
    private val transaction: TransactionRunner,
    private val clock: Clock,
    private val reminders: ReminderScheduler,
) {
    suspend fun create(draft: TaskDraft): Long {
        require(draft.title.isNotBlank())
        val dueAt = draft.date?.atTime(draft.time ?: LocalTime.MIDNIGHT)?.toMillis(clock.zone())
        val settings = listOfNotNull(draft.reminder.takeIf { dueAt != null })
        return reminders.createTask(transaction, settings) {
            tasks.add(
                title = draft.title,
                dueAt = dueAt,
                allDay = draft.time == null,
                priority = draft.priority,
                listId = draft.listId,
                tagIds = draft.tagIds,
            )
        }
    }
}
