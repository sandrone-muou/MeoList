package com.meolist.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.meolist.data.db.dao.ActivityTypeDao
import com.meolist.data.db.dao.ReminderDao
import com.meolist.data.db.dao.TagDao
import com.meolist.data.db.dao.TaskDao
import com.meolist.data.db.dao.TaskListDao
import com.meolist.data.db.dao.TimeRecordDao
import com.meolist.data.db.entity.ActivityGoalEntity
import com.meolist.data.db.entity.ActivityTypeEntity
import com.meolist.data.db.entity.GoalReachedEntity
import com.meolist.data.db.entity.ListFolderEntity
import com.meolist.data.db.entity.RecordEntity
import com.meolist.data.db.entity.ReminderEntity
import com.meolist.data.db.entity.RunningTimerEntity
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
        ActivityTypeEntity::class,
        ActivityGoalEntity::class,
        GoalReachedEntity::class,
        RunningTimerEntity::class,
        RecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MeoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun tagDao(): TagDao
    abstract fun reminderDao(): ReminderDao
    abstract fun activityTypeDao(): ActivityTypeDao
    abstract fun timeRecordDao(): TimeRecordDao

    companion object {
        const val NAME = "meolist.db"
    }
}
