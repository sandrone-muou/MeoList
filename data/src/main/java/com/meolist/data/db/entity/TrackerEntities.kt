package com.meolist.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "activity_type")
data class ActivityTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    val sortOrder: Long,
    val archived: Boolean,
)

@Entity(
    tableName = "activity_goal",
    primaryKeys = ["typeId", "period"],
    foreignKeys = [
        ForeignKey(entity = ActivityTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ActivityGoalEntity(
    val typeId: Long,
    val period: Int,
    val targetSec: Long,
)

/** One row per goal period that was reached; keeps the announcement (and later the farm bonus) one-off. */
@Entity(
    tableName = "goal_reached",
    primaryKeys = ["typeId", "period", "periodStartDay"],
    foreignKeys = [
        ForeignKey(entity = ActivityTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class GoalReachedEntity(
    val typeId: Long,
    val period: Int,
    val periodStartDay: Long,
    val reachedAt: Long,
)

@Entity(
    tableName = "running_timer",
    foreignKeys = [
        ForeignKey(entity = ActivityTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("typeId", unique = true), Index("taskId")],
)
data class RunningTimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: Long,
    val taskId: Long?,
    val startedAt: Long,
    val comment: String,
)

@Entity(
    tableName = "record",
    foreignKeys = [
        ForeignKey(entity = ActivityTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("typeId"), Index("taskId"), Index("startAt"), Index("endAt")],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: Long,
    val taskId: Long?,
    val startAt: Long,
    val endAt: Long,
    val comment: String,
    val isManual: Boolean,
)
