package com.meolist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.meolist.data.db.entity.ListFolderEntity
import com.meolist.data.db.entity.TagEntity
import com.meolist.data.db.entity.TaskEntity
import com.meolist.data.db.entity.TaskListEntity
import com.meolist.data.db.entity.TaskTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM task WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE deletedAt IS NOT NULL")
    fun observeTrash(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE id = :id")
    fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM task WHERE parentId = :parentId AND deletedAt IS NULL ORDER BY sortOrder, id")
    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM task
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :pattern || '%' ESCAPE '\' OR note LIKE '%' || :pattern || '%' ESCAPE '\')
        ORDER BY status, updatedAt DESC
        LIMIT 200
        """,
    )
    fun search(pattern: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM task WHERE parentId = :parentId")
    suspend fun getSubtasks(parentId: Long): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE task SET deletedAt = :at WHERE (id = :id OR parentId = :id) AND deletedAt IS NULL")
    suspend fun moveToTrash(id: Long, at: Long)

    @Query("UPDATE task SET deletedAt = NULL WHERE id = :id OR parentId = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM task WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM task WHERE listId IS :listId")
    suspend fun nextSortOrder(listId: Long?): Long

    @Query("UPDATE task SET deletedAt = :at, listId = NULL WHERE listId = :listId AND deletedAt IS NULL")
    suspend fun trashAllInList(listId: Long, at: Long)

    // Tags ------------------------------------------------------------------------------------

    @Query("SELECT * FROM task_tag")
    fun observeTaskTags(): Flow<List<TaskTagEntity>>

    @Query("SELECT tagId FROM task_tag WHERE taskId = :taskId")
    fun observeTagIds(taskId: Long): Flow<List<Long>>

    @Query("DELETE FROM task_tag WHERE taskId = :taskId")
    suspend fun clearTags(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(refs: List<TaskTagEntity>)

    @Transaction
    suspend fun setTags(taskId: Long, tagIds: Set<Long>) {
        clearTags(taskId)
        insertTags(tagIds.map { TaskTagEntity(taskId, it) })
    }
}

@Dao
interface TaskListDao {
    @Query("SELECT * FROM task_list ORDER BY sortOrder, id")
    fun observeLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM list_folder ORDER BY sortOrder, id")
    fun observeFolders(): Flow<List<ListFolderEntity>>

    @Query("SELECT * FROM task_list WHERE id = :id")
    suspend fun getList(id: Long): TaskListEntity?

    @Upsert
    suspend fun upsertList(list: TaskListEntity): Long

    @Query("DELETE FROM task_list WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM task_list")
    suspend fun nextListSortOrder(): Long

    @Upsert
    suspend fun upsertFolder(folder: ListFolderEntity): Long

    @Query("DELETE FROM list_folder WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM list_folder")
    suspend fun nextFolderSortOrder(): Long
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Upsert
    suspend fun upsert(tag: TagEntity): Long

    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun delete(id: Long)
}
