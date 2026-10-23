package com.meolist.domain.task

import com.meolist.domain.common.Clock
import javax.inject.Inject

/**
 * Write operations on tasks. Screens call this instead of the repository so that
 * follow-up work (repeat instances, reminders, farm nutrients) has one entry point.
 */
class TaskInteractor @Inject constructor(
    private val repo: TaskRepository,
    private val clock: Clock,
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
        val merged = stored.copy(
            title = edited.title.trim().ifEmpty { stored.title },
            note = edited.note,
            priority = edited.priority,
            startAt = edited.startAt,
            dueAt = edited.dueAt,
            allDay = edited.allDay,
            listId = if (stored.parentId == null) edited.listId else stored.listId,
            repeatRule = edited.repeatRule,
            repeatFromCompletion = edited.repeatFromCompletion,
        )
        if (merged == stored) return
        val now = clock.now()
        repo.update(merged.copy(updatedAt = now))
        // Subtasks always follow their parent's list.
        if (stored.parentId == null && merged.listId != stored.listId) {
            repo.getSubtasks(stored.id).forEach { repo.update(it.copy(listId = merged.listId, updatedAt = now)) }
        }
    }

    suspend fun setDone(id: Long, done: Boolean) {
        val task = repo.get(id) ?: return
        if (task.isDone == done) return
        val now = clock.now()
        repo.update(
            task.copy(
                status = if (done) TaskStatus.DONE else TaskStatus.TODO,
                completedAt = if (done) now else null,
                updatedAt = now,
            ),
        )
    }

    suspend fun moveToTrash(id: Long) = repo.moveToTrash(id, clock.now())

    suspend fun restore(id: Long) {
        val task = repo.get(id) ?: return
        // Restoring a subtask whose parent is still in the trash restores the parent too.
        task.parentId?.let { parentId -> repo.get(parentId)?.takeIf { it.isDeleted }?.let { repo.restore(it.id) } }
        repo.restore(id)
    }

    suspend fun deletePermanently(id: Long) = repo.deletePermanently(id)

    suspend fun emptyTrash() = repo.emptyTrash()

    suspend fun setTags(taskId: Long, tagIds: Set<Long>) = repo.setTags(taskId, tagIds)
}
