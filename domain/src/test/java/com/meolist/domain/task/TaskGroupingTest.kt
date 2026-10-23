package com.meolist.domain.task

import com.meolist.domain.common.startOfDayMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskGroupingTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 3, 10)

    private fun due(daysFromToday: Long, hour: Int = 9): Long =
        today.plusDays(daysFromToday).startOfDayMillis(zone) + hour * 3_600_000L

    private fun task(
        id: Long,
        dueDays: Long? = null,
        listId: Long? = null,
        parentId: Long? = null,
        priority: Priority = Priority.NONE,
        done: Boolean = false,
        completedAt: Long? = null,
        deletedAt: Long? = null,
        createdAt: Long = id,
    ) = Task(
        id = id,
        listId = listId,
        parentId = parentId,
        title = "t$id",
        priority = priority,
        status = if (done) TaskStatus.DONE else TaskStatus.TODO,
        completedAt = completedAt,
        dueAt = dueDays?.let { due(it) },
        createdAt = createdAt,
        deletedAt = deletedAt,
    )

    private fun snapshot(
        tasks: List<Task>,
        lists: List<TaskList> = emptyList(),
        tags: List<Tag> = emptyList(),
        taskTags: List<TaskTagRef> = emptyList(),
    ) = TaskSnapshot(tasks, lists, tags, taskTags)

    private fun List<TaskGroup>.ids(section: TaskSection) =
        first { it.section == section }.items.map { it.task.id }

    @Test
    fun sectionOf_bucketsByDueDate() {
        assertEquals(TaskSection.OVERDUE, TaskGrouping.sectionOf(task(1, -1), today, zone))
        assertEquals(TaskSection.TODAY, TaskGrouping.sectionOf(task(1, 0), today, zone))
        assertEquals(TaskSection.TOMORROW, TaskGrouping.sectionOf(task(1, 1), today, zone))
        assertEquals(TaskSection.NEXT_7_DAYS, TaskGrouping.sectionOf(task(1, 6), today, zone))
        assertEquals(TaskSection.LATER, TaskGrouping.sectionOf(task(1, 7), today, zone))
        assertEquals(TaskSection.NO_DATE, TaskGrouping.sectionOf(task(1), today, zone))
        assertEquals(TaskSection.COMPLETED, TaskGrouping.sectionOf(task(1, 0, done = true), today, zone))
    }

    @Test
    fun sectionOf_lateEveningStillToday() {
        val lateToday = task(1).copy(dueAt = due(0, hour = 23) + 59 * 60_000L)
        assertEquals(TaskSection.TODAY, TaskGrouping.sectionOf(lateToday, today, zone))
    }

    @Test
    fun all_groupsInDisplayOrder_completedLast() {
        val groups = TaskGrouping.group(
            TaskFilter.All,
            snapshot(listOf(task(1), task(2, 3), task(3, 0), task(4, -2), task(5, 0, done = true, completedAt = 99))),
            today,
            zone,
        )
        assertEquals(
            listOf(TaskSection.OVERDUE, TaskSection.TODAY, TaskSection.NEXT_7_DAYS, TaskSection.NO_DATE, TaskSection.COMPLETED),
            groups.map { it.section },
        )
    }

    @Test
    fun openTasks_sortedByPriorityThenDue() {
        val tasks = listOf(
            task(1, 0, priority = Priority.LOW),
            task(2, 0, priority = Priority.HIGH),
            task(3, 0, priority = Priority.LOW).copy(dueAt = due(0, hour = 8)),
        )
        val groups = TaskGrouping.group(TaskFilter.All, snapshot(tasks), today, zone)
        assertEquals(listOf(2L, 3L, 1L), groups.ids(TaskSection.TODAY))
    }

    @Test
    fun completed_sortedByCompletionDesc() {
        val tasks = listOf(task(1, done = true, completedAt = 10), task(2, done = true, completedAt = 30), task(3, done = true, completedAt = 20))
        val groups = TaskGrouping.group(TaskFilter.Completed, snapshot(tasks), today, zone)
        assertEquals(listOf(TaskSection.COMPLETED), groups.map { it.section })
        assertEquals(listOf(2L, 3L, 1L), groups.ids(TaskSection.COMPLETED))
    }

    @Test
    fun today_includesOverdueOpen_butOnlyTodaysDone() {
        val tasks = listOf(
            task(1, -3),
            task(2, 0),
            task(3, 1),
            task(4),
            task(5, -1, done = true, completedAt = 1),
            task(6, 0, done = true, completedAt = 2),
        )
        val ids = TaskGrouping.group(TaskFilter.Today, snapshot(tasks), today, zone).flatMap { g -> g.items.map { it.task.id } }
        assertEquals(setOf(1L, 2L, 6L), ids.toSet())
    }

    @Test
    fun next7_coversTodayThroughSixDaysAhead() {
        val tasks = listOf(task(1, -1), task(2, 6), task(3, 7), task(4, -1, done = true), task(5, 6, done = true))
        val ids = TaskGrouping.group(TaskFilter.Next7Days, snapshot(tasks), today, zone).flatMap { g -> g.items.map { it.task.id } }
        assertEquals(setOf(1L, 2L, 5L), ids.toSet())
    }

    @Test
    fun inboxAndList_filterByListId() {
        val tasks = listOf(task(1), task(2, listId = 7), task(3, listId = 8))
        val lists = listOf(TaskList(id = 7, name = "Work", color = 0), TaskList(id = 8, name = "Home", color = 0))
        val snap = snapshot(tasks, lists)
        assertEquals(listOf(1L), TaskGrouping.group(TaskFilter.Inbox, snap, today, zone).flatMap { g -> g.items.map { it.task.id } })
        assertEquals(listOf(2L), TaskGrouping.group(TaskFilter.OfList(7), snap, today, zone).flatMap { g -> g.items.map { it.task.id } })
    }

    @Test
    fun archivedLists_hiddenFromSmartFilters_butVisibleDirectly() {
        val tasks = listOf(task(1, 0, listId = 7), task(2, 0))
        val lists = listOf(TaskList(id = 7, name = "Old", color = 0, archived = true))
        val snap = snapshot(tasks, lists)
        for (filter in listOf(TaskFilter.All, TaskFilter.Today, TaskFilter.Next7Days)) {
            val ids = TaskGrouping.group(filter, snap, today, zone).flatMap { g -> g.items.map { it.task.id } }
            assertEquals("filter $filter", listOf(2L), ids)
        }
        assertEquals(1, TaskGrouping.openCount(TaskFilter.OfList(7), snap, today, zone))
    }

    @Test
    fun subtasks_areNotTopLevel_andCountedOnParent() {
        val tasks = listOf(
            task(1, 0),
            task(2, 0, parentId = 1),
            task(3, parentId = 1, done = true),
            task(4, parentId = 1),
        )
        val groups = TaskGrouping.group(TaskFilter.All, snapshot(tasks), today, zone)
        val items = groups.flatMap { it.items }
        assertEquals(listOf(1L), items.map { it.task.id })
        assertEquals(3, items.single().subtaskTotal)
        assertEquals(1, items.single().subtaskDone)
    }

    @Test
    fun tagFilter_andTagsSortedByName() {
        val tags = listOf(Tag(1, "zeta", 0), Tag(2, "alpha", 0))
        val refs = listOf(TaskTagRef(1, 1), TaskTagRef(1, 2), TaskTagRef(2, 1))
        val snap = snapshot(listOf(task(1), task(2), task(3)), tags = tags, taskTags = refs)
        val groups = TaskGrouping.group(TaskFilter.OfTag(2), snap, today, zone)
        val item = groups.flatMap { it.items }.single()
        assertEquals(1L, item.task.id)
        assertEquals(listOf("alpha", "zeta"), item.tags.map { it.name })
    }

    @Test
    fun openCount_ignoresDoneTasks() {
        val snap = snapshot(listOf(task(1, 0), task(2, 0, done = true), task(3, -1)))
        assertEquals(2, TaskGrouping.openCount(TaskFilter.Today, snap, today, zone))
    }

    @Test
    fun trash_hidesSubtasksDeletedWithParent_newestFirst() {
        val deleted = listOf(
            task(1, deletedAt = 100),
            task(2, parentId = 1, deletedAt = 100),
            task(3, parentId = 9, deletedAt = 300),
            task(4, deletedAt = 200),
        )
        val groups = TaskGrouping.trash(deleted, emptyList())
        assertEquals(listOf(3L, 4L, 1L), groups.single().items.map { it.task.id })
        assertTrue(TaskGrouping.trash(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun filterKey_roundTrips() {
        val filters = listOf(
            TaskFilter.Inbox, TaskFilter.Today, TaskFilter.Next7Days, TaskFilter.All,
            TaskFilter.Completed, TaskFilter.Trash, TaskFilter.OfList(42), TaskFilter.OfTag(7),
        )
        filters.forEach { assertEquals(it, TaskFilter.fromKey(it.key())) }
        assertEquals(TaskFilter.Today, TaskFilter.fromKey(null))
        assertEquals(TaskFilter.Today, TaskFilter.fromKey("list:abc"))
        assertEquals(TaskFilter.Today, TaskFilter.fromKey("bogus"))
    }
}
