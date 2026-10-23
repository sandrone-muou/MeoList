package com.meolist.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.meolist.data.db.dao.ReminderDao
import com.meolist.data.db.dao.TagDao
import com.meolist.data.db.dao.TaskDao
import com.meolist.data.db.dao.TaskListDao
import com.meolist.data.db.entity.ListFolderEntity
import com.meolist.data.db.entity.ReminderEntity
import com.meolist.data.db.entity.TagEntity
import com.meolist.data.db.entity.TaskEntity
import com.meolist.data.db.entity.TaskListEntity
import com.meolist.data.db.entity.TaskTagEntity

@Database(
    entities = [
        ListFolderEntity::class,
        TaskListEntity::class,
        TaskEntity::class,
        TagEntity::class,
        TaskTagEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MeoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun tagDao(): TagDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "meolist.db"
    }
}
