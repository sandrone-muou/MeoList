package com.meolist.data.repo

import com.meolist.data.db.MeoDatabase
import com.meolist.data.db.dao.TagDao
import com.meolist.data.db.dao.TaskDao
import com.meolist.data.db.dao.TaskListDao
import com.meolist.data.db.entity.ListFolderEntity
import com.meolist.data.db.entity.TagEntity
import com.meolist.data.db.entity.TaskEntity
import com.meolist.data.db.entity.TaskListEntity
import com.meolist.domain.task.ListFolder
import com.meolist.domain.task.Priority
import com.meolist.domain.task.Tag
import com.meolist.domain.task.TagRepository
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskList
import com.meolist.domain.task.TaskListRepository
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.task.TaskStatus
import com.meolist.domain.task.TaskTagRef
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal fun TaskEntity.toDomain() = Task(
    id = id,
    listId = listId,
    parentId = parentId,
    title = title,
    note = note,
    priority = Priority.fromValue(priority),
    status = TaskStatus.fromValue(status),
    completedAt = completedAt,
    startAt = startAt,
    dueAt = dueAt,
    allDay = allDay,
    repeatRule = repeatRule,
    repeatFromCompletion = repeatFromCompletion,
    repeatOriginId = repeatOriginId,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Task.toEntity() = TaskEntity(
    id = id,
    listId = listId,
    parentId = parentId,
    title = title,
    note = note,
    priority = priority.value,
    status = status.value,
    completedAt = completedAt,
    startAt = startAt,
    dueAt = dueAt,
    allDay = allDay,
    repeatRule = repeatRule,
    repeatFromCompletion = repeatFromCompletion,
    repeatOriginId = repeatOriginId,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TaskListEntity.toDomain() =
    TaskList(id, folderId, name, color, sortOrder, defaultActivityTypeId, archived)

internal fun TaskList.toEntity() =
    TaskListEntity(id, folderId, name, color, sortOrder, defaultActivityTypeId, archived)

internal fun ListFolderEntity.toDomain() = ListFolder(id, name, sortOrder)
internal fun TagEntity.toDomain() = Tag(id, name, color)

/** Escapes LIKE wildcards so user input is matched literally. */
internal fun likeLiteral(query: String): String =
    query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** Room's @Upsert returns -1 when it updated an existing row. */
private fun upsertedId(result: Long, id: Long) = if (result == -1L) id else result

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {
    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { it.map(TaskEntity::toDomain) }
    override fun observeTrash(): Flow<List<Task>> = dao.observeTrash().map { it.map(TaskEntity::toDomain) }
    override fun observeTask(id: Long): Flow<Task?> = dao.observe(id).map { it?.toDomain() }
    override fun observeSubtasks(parentId: Long): Flow<List<Task>> =
        dao.observeSubtasks(parentId).map { it.map(TaskEntity::toDomain) }

    override fun search(query: String): Flow<List<Task>> {
        val q = query.trim()
        if (q.isEmpty()) return flowOf(emptyList())
        return dao.search(likeLiteral(q)).map { it.map(TaskEntity::toDomain) }
    }

    override suspend fun get(id: Long): Task? = dao.get(id)?.toDomain()
    override suspend fun getSubtasks(parentId: Long): List<Task> = dao.getSubtasks(parentId).map { it.toDomain() }
    override suspend fun insert(task: Task): Long = dao.insert(task.toEntity().copy(id = 0))
    override suspend fun update(task: Task) = dao.update(task.toEntity())
    override suspend fun moveToTrash(id: Long, at: Long) = dao.moveToTrash(id, at)
    override suspend fun restore(id: Long) = dao.restore(id)
    override suspend fun deletePermanently(id: Long) = dao.delete(id)
    override suspend fun emptyTrash() = dao.emptyTrash()
    override suspend fun nextSortOrder(listId: Long?): Long = dao.nextSortOrder(listId)

    override fun observeTaskTags(): Flow<List<TaskTagRef>> =
        dao.observeTaskTags().map { refs -> refs.map { TaskTagRef(it.taskId, it.tagId) } }

    override fun observeTagIds(taskId: Long): Flow<List<Long>> = dao.observeTagIds(taskId)
    override suspend fun setTags(taskId: Long, tagIds: Set<Long>) = dao.setTags(taskId, tagIds)
}

class TaskListRepositoryImpl @Inject constructor(
    private val db: MeoDatabase,
    private val dao: TaskListDao,
    private val taskDao: TaskDao,
) : TaskListRepository {
    override fun observeLists(): Flow<List<TaskList>> = dao.observeLists().map { it.map(TaskListEntity::toDomain) }
    override fun observeFolders(): Flow<List<ListFolder>> =
        dao.observeFolders().map { it.map(ListFolderEntity::toDomain) }

    override suspend fun getList(id: Long): TaskList? = dao.getList(id)?.toDomain()

    override suspend fun saveList(list: TaskList): Long {
        val entity = if (list.id == 0L) list.copy(sortOrder = dao.nextListSortOrder()).toEntity() else list.toEntity()
        return upsertedId(dao.upsertList(entity), list.id)
    }

    override suspend fun deleteList(id: Long, at: Long) = db.withTransaction {
        taskDao.trashAllInList(id, at)
        dao.deleteList(id)
    }

    override suspend fun saveFolder(folder: ListFolder): Long {
        val order = if (folder.id == 0L) dao.nextFolderSortOrder() else folder.sortOrder
        return upsertedId(dao.upsertFolder(ListFolderEntity(folder.id, folder.name, order)), folder.id)
    }

    override suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)
}

class TagRepositoryImpl @Inject constructor(
    private val dao: TagDao,
) : TagRepository {
    override fun observeTags(): Flow<List<Tag>> = dao.observeTags().map { it.map(TagEntity::toDomain) }
    override suspend fun saveTag(tag: Tag): Long =
        upsertedId(dao.upsert(TagEntity(tag.id, tag.name.trim(), tag.color)), tag.id)

    override suspend fun deleteTag(id: Long) = dao.delete(id)
}
