package com.meolist.domain.task

import com.meolist.domain.common.toLocalDate
import java.time.LocalDate
import java.time.ZoneId

/** Date buckets shown as headers in task lists, in display order. */
enum class TaskSection { OVERDUE, TODAY, TOMORROW, NEXT_7_DAYS, LATER, NO_DATE, COMPLETED, TRASH }

/** A top-level task decorated with what a row needs to render. */
data class TaskItem(
    val task: Task,
    val list: TaskList?,
    val tags: List<Tag>,
    val subtaskTotal: Int,
    val subtaskDone: Int,
)

data class TaskGroup(val section: TaskSection, val items: List<TaskItem>)

/** Snapshot of everything needed to build task screens; pure data so the logic is unit-testable. */
data class TaskSnapshot(
    /** Every non-deleted task, including subtasks. */
    val tasks: List<Task>,
    val lists: List<TaskList>,
    val tags: List<Tag>,
    val taskTags: List<TaskTagRef>,
)

object TaskGrouping {

    private val openComparator = compareByDescending<TaskItem> { it.task.priority.value }
        .thenBy(nullsLast()) { it.task.dueAt }
        .thenBy { it.task.sortOrder }
        .thenBy { it.task.createdAt }

    fun group(
        filter: TaskFilter,
        snapshot: TaskSnapshot,
        today: LocalDate,
        zone: ZoneId,
    ): List<TaskGroup> {
        val items = decorate(snapshot).filter { matches(filter, it, today, zone) }
        val (done, open) = items.partition { it.task.isDone }

        val groups = mutableListOf<TaskGroup>()
        if (filter != TaskFilter.Completed) {
            open.groupBy { sectionOf(it.task, today, zone) }
                .toSortedMap()
                .forEach { (section, list) -> groups += TaskGroup(section, list.sortedWith(openComparator)) }
        }
        if (done.isNotEmpty()) {
            groups += TaskGroup(TaskSection.COMPLETED, done.sortedByDescending { it.task.completedAt ?: 0L })
        }
        return groups
    }

    /** Number of open top-level tasks matching [filter]; used for drawer badges. */
    fun openCount(filter: TaskFilter, snapshot: TaskSnapshot, today: LocalDate, zone: ZoneId): Int =
        decorate(snapshot).count { !it.task.isDone && matches(filter, it, today, zone) }

    fun sectionOf(task: Task, today: LocalDate, zone: ZoneId): TaskSection {
        if (task.isDone) return TaskSection.COMPLETED
        val due = task.dueAt?.toLocalDate(zone) ?: return TaskSection.NO_DATE
        return when {
            due < today -> TaskSection.OVERDUE
            due == today -> TaskSection.TODAY
            due == today.plusDays(1) -> TaskSection.TOMORROW
            due <= today.plusDays(6) -> TaskSection.NEXT_7_DAYS
            else -> TaskSection.LATER
        }
    }

    /**
     * Builds the trash view (newest deletion first). Subtasks deleted together with their parent
     * are hidden behind it; individually deleted subtasks are listed on their own.
     */
    fun trash(deleted: List<Task>, lists: List<TaskList>): List<TaskGroup> {
        if (deleted.isEmpty()) return emptyList()
        val listById = lists.associateBy { it.id }
        val deletedIds = deleted.mapTo(HashSet()) { it.id }
        val items = deleted.filter { it.parentId == null || it.parentId !in deletedIds }
            .sortedByDescending { it.deletedAt ?: 0L }
            .map { TaskItem(it, it.listId?.let(listById::get), emptyList(), 0, 0) }
        return listOf(TaskGroup(TaskSection.TRASH, items))
    }

    private fun decorate(snapshot: TaskSnapshot): List<TaskItem> {
        val listById = snapshot.lists.associateBy { it.id }
        val tagById = snapshot.tags.associateBy { it.id }
        val tagsByTask = snapshot.taskTags.groupBy({ it.taskId }, { it.tagId })
        val subtasks = snapshot.tasks.filter { it.parentId != null }.groupBy { it.parentId }
        return snapshot.tasks.filter { it.parentId == null }.map { task ->
            val subs = subtasks[task.id].orEmpty()
            TaskItem(
                task = task,
                list = task.listId?.let(listById::get),
                tags = tagsByTask[task.id].orEmpty().mapNotNull(tagById::get).sortedBy { it.name },
                subtaskTotal = subs.size,
                subtaskDone = subs.count { it.isDone },
            )
        }
    }

    private fun matches(
        filter: TaskFilter,
        item: TaskItem,
        today: LocalDate,
        zone: ZoneId,
    ): Boolean {
        val task = item.task
        val inVisibleList = item.list?.archived != true
        val due = task.dueAt?.toLocalDate(zone)
        return when (filter) {
            TaskFilter.Inbox -> task.listId == null
            TaskFilter.Today -> inVisibleList && due != null &&
                if (task.isDone) due == today else due <= today
            TaskFilter.Next7Days -> inVisibleList && due != null &&
                if (task.isDone) due >= today && due <= today.plusDays(6) else due <= today.plusDays(6)
            TaskFilter.All -> inVisibleList
            TaskFilter.Completed -> inVisibleList && task.isDone
            TaskFilter.Trash -> false
            is TaskFilter.OfList -> task.listId == filter.listId
            is TaskFilter.OfTag -> inVisibleList && item.tags.any { it.id == filter.tagId }
        }
    }
}
