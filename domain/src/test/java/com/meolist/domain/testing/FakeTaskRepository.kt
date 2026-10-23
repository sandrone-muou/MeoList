package com.meolist.domain.testing

import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.task.TaskTagRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TaskRepository] that mimics the Room implementation's semantics. */
class FakeTaskRepository : TaskRepository {
    private val tasks = MutableStateFlow<Map<Long, Task>>(emptyMap())
    private val tags = MutableStateFlow<Set<TaskTagRef>>(emptySet())
    private var nextId = 1L

    val all: List<Task> get() = tasks.value.values.sortedBy { it.id }

    override fun observeAll(): Flow<List<Task>> = tasks.map { m -> m.values.filter { it.deletedAt == null } }
    override fun observeTrash(): Flow<List<Task>> = tasks.map { m -> m.values.filter { it.deletedAt != null } }
    override fun observeTask(id: Long): Flow<Task?> = tasks.map { it[id] }
    override fun observeSubtasks(parentId: Long): Flow<List<Task>> =
        tasks.map { m -> m.values.filter { it.parentId == parentId && it.deletedAt == null } }

    override fun search(query: String): Flow<List<Task>> =
        tasks.map { m -> m.values.filter { it.deletedAt == null && it.title.contains(query, ignoreCase = true) } }

    override suspend fun get(id: Long): Task? = tasks.value[id]
    override suspend fun getSubtasks(parentId: Long): List<Task> = tasks.value.values.filter { it.parentId == parentId }

    override suspend fun insert(task: Task): Long {
        val id = nextId++
        tasks.value = tasks.value + (id to task.copy(id = id))
        return id
    }

    override suspend fun update(task: Task) {
        check(task.id in tasks.value) { "unknown task ${task.id}" }
        tasks.value = tasks.value + (task.id to task)
    }

    override suspend fun moveToTrash(id: Long, at: Long) = edit({ it.id == id || it.parentId == id }) { it.copy(deletedAt = at) }

    override suspend fun restore(id: Long) = edit({ it.id == id || it.parentId == id }) { it.copy(deletedAt = null) }

    override suspend fun deletePermanently(id: Long) {
        tasks.value = tasks.value.filterValues { it.id != id && it.parentId != id }
    }

    override suspend fun emptyTrash() {
        val doomed = tasks.value.values.filter { it.deletedAt != null }.map { it.id }.toSet()
        tasks.value = tasks.value.filterValues { it.id !in doomed && it.parentId !in doomed }
    }

    override suspend fun nextSortOrder(listId: Long?): Long =
        (tasks.value.values.filter { it.listId == listId }.maxOfOrNull { it.sortOrder } ?: 0L) + 1

    override fun observeTaskTags(): Flow<List<TaskTagRef>> = tags.map { it.toList() }
    override fun observeTagIds(taskId: Long): Flow<List<Long>> = tags.map { s -> s.filter { it.taskId == taskId }.map { it.tagId } }

    override suspend fun getTagIds(taskId: Long): Set<Long> = tagIdsOf(taskId)

    override suspend fun setTags(taskId: Long, tagIds: Set<Long>) {
        tags.value = tags.value.filterNot { it.taskId == taskId }.toSet() + tagIds.map { TaskTagRef(taskId, it) }
    }

    fun tagIdsOf(taskId: Long): Set<Long> = tags.value.filter { it.taskId == taskId }.map { it.tagId }.toSet()

    private fun edit(predicate: (Task) -> Boolean, change: (Task) -> Task) {
        tasks.value = tasks.value.mapValues { (_, t) -> if (predicate(t)) change(t) else t }
    }
}
