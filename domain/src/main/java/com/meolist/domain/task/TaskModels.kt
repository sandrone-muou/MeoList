package com.meolist.domain.task

enum class Priority(val value: Int) {
    NONE(0), LOW(1), MEDIUM(2), HIGH(3);

    companion object {
        fun fromValue(value: Int): Priority = entries.firstOrNull { it.value == value } ?: NONE
    }
}

enum class TaskStatus(val value: Int) {
    TODO(0), DONE(1);

    companion object {
        fun fromValue(value: Int): TaskStatus = if (value == DONE.value) DONE else TODO
    }
}

/**
 * A task or (when [parentId] is set) a one-level subtask.
 * [listId] null means the task lives in the Inbox.
 * Times are epoch milliseconds; when [allDay] is true only the date part of [dueAt] matters.
 */
data class Task(
    val id: Long = 0,
    val listId: Long? = null,
    val parentId: Long? = null,
    val title: String,
    val note: String = "",
    val priority: Priority = Priority.NONE,
    val status: TaskStatus = TaskStatus.TODO,
    val completedAt: Long? = null,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val allDay: Boolean = true,
    val repeatRule: String? = null,
    val repeatFromCompletion: Boolean = false,
    val repeatOriginId: Long? = null,
    val sortOrder: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
) {
    val isDone: Boolean get() = status == TaskStatus.DONE
    val isDeleted: Boolean get() = deletedAt != null
}

data class ListFolder(
    val id: Long = 0,
    val name: String,
    val sortOrder: Long = 0,
)

data class TaskList(
    val id: Long = 0,
    val folderId: Long? = null,
    val name: String,
    val color: Int,
    val sortOrder: Long = 0,
    val defaultActivityTypeId: Long? = null,
    val archived: Boolean = false,
)

data class Tag(
    val id: Long = 0,
    val name: String,
    val color: Int,
)

data class TaskTagRef(val taskId: Long, val tagId: Long)

/** Which tasks a task screen shows. */
sealed interface TaskFilter {
    data object Inbox : TaskFilter
    data object Today : TaskFilter
    data object Next7Days : TaskFilter
    data object All : TaskFilter
    data object Completed : TaskFilter
    data object Trash : TaskFilter
    data class OfList(val listId: Long) : TaskFilter
    data class OfTag(val tagId: Long) : TaskFilter

    /** Stable string form, used to remember the last opened filter. */
    fun key(): String = when (this) {
        Inbox -> "inbox"
        Today -> "today"
        Next7Days -> "next7"
        All -> "all"
        Completed -> "completed"
        Trash -> "trash"
        is OfList -> "list:$listId"
        is OfTag -> "tag:$tagId"
    }

    companion object {
        fun fromKey(key: String?): TaskFilter = when {
            key == null -> Today
            key == "inbox" -> Inbox
            key == "today" -> Today
            key == "next7" -> Next7Days
            key == "all" -> All
            key == "completed" -> Completed
            key == "trash" -> Trash
            key.startsWith("list:") -> key.removePrefix("list:").toLongOrNull()?.let { OfList(it) } ?: Today
            key.startsWith("tag:") -> key.removePrefix("tag:").toLongOrNull()?.let { OfTag(it) } ?: Today
            else -> Today
        }
    }
}
