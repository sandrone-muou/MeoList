package com.meolist.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "activity_subproject",
    foreignKeys = [ForeignKey(entity = ActivityTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("typeId")],
)
data class ActivitySubprojectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: Long,
    val name: String,
    val color: Int,
    val sortOrder: Long,
    val archived: Boolean,
)

@Entity(
    tableName = "timer_stage",
    foreignKeys = [
        ForeignKey(entity = RunningTimerEntity::class, parentColumns = ["id"], childColumns = ["runningId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["runningId", "position"], unique = true), Index(value = ["recordId", "position"], unique = true)],
)
data class TimerStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runningId: Long? = null,
    val recordId: Long? = null,
    val position: Int,
    val subprojectId: Long?,
    val name: String,
    val color: Int,
    val startAt: Long?,
    val endAt: Long?,
)

data class RunningWithStages(
    @Embedded val timer: RunningTimerEntity,
    @Relation(parentColumn = "id", entityColumn = "runningId") val stages: List<TimerStageEntity>,
)

data class RecordWithStages(
    @Embedded val record: RecordEntity,
    @Relation(parentColumn = "id", entityColumn = "recordId") val stages: List<TimerStageEntity>,
)
