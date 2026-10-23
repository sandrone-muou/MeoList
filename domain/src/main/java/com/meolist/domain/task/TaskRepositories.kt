package com.meolist.domain.task

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    /** Every non-deleted task, including subtasks. */
    fun observeAll(): Flow<List<Task>>
    /** Deleted tasks (trash), including subtasks. */
    fun observeTrash(): Flow<List<Task>>
    fun observeTask(id: Long): Flow<Task?>
    fun observeSubtasks(parentId: Long): Flow<List<Task>>
    fun search(query: String): Flow<List<Task>>

    suspend fun get(id: Long): Task?
    suspend fun getSubtasks(parentId: Long): List<Task>
    suspend fun insert(task: Task): Long
    suspend fun update(task: Task)

    /** Marks the task and its subtasks as deleted (moves them to the trash). */
    suspend fun moveToTrash(id: Long, at: Long)
    suspend fun restore(id: Long)
    /** Permanently removes a task; subtasks and tag links cascade. */
    suspend fun deletePermanently(id: Long)
    suspend fun emptyTrash()

    suspend fun nextSortOrder(listId: Long?): Long

    fun observeTaskTags(): Flow<List<TaskTagRef>>
    fun observeTagIds(taskId: Long): Flow<List<Long>>
    suspend fun getTagIds(taskId: Long): Set<Long>
    suspend fun setTags(taskId: Long, tagIds: Set<Long>)
}

interface TaskListRepository {
    fun observeLists(): Flow<List<TaskList>>
    fun observeFolders(): Flow<List<ListFolder>>
    suspend fun getList(id: Long): TaskList?
    suspend fun saveList(list: TaskList): Long
    /** Deletes the list and moves its tasks to the trash (restoring puts them in the Inbox). */
    suspend fun deleteList(id: Long, at: Long)
    suspend fun saveFolder(folder: ListFolder): Long
    /** Deletes the folder; its lists become ungrouped. */
    suspend fun deleteFolder(id: Long)
}

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>
    suspend fun saveTag(tag: Tag): Long
    suspend fun deleteTag(id: Long)
}
