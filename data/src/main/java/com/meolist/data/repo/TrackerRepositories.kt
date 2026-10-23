package com.meolist.data.repo

import androidx.room.withTransaction
import com.meolist.data.db.MeoDatabase
import com.meolist.data.db.dao.ActivityTypeDao
import com.meolist.data.db.dao.TimeRecordDao
import com.meolist.data.db.entity.ActivityGoalEntity
import com.meolist.data.db.entity.ActivityTypeEntity
import com.meolist.data.db.entity.GoalReachedEntity
import com.meolist.data.db.entity.RecordEntity
import com.meolist.data.db.entity.RunningTimerEntity
import com.meolist.domain.common.TransactionRunner
import com.meolist.domain.tracker.ActivityGoal
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TimeRecordRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun ActivityTypeEntity.toDomain() = ActivityType(id, name, icon, color, sortOrder, archived)
internal fun ActivityType.toEntity() = ActivityTypeEntity(id, name, icon, color, sortOrder, archived)
internal fun ActivityGoalEntity.toDomain() = ActivityGoal(typeId, GoalPeriod.fromValue(period), targetSec)
internal fun ActivityGoal.toEntity() = ActivityGoalEntity(typeId, period.value, targetSec)
internal fun RunningTimerEntity.toDomain() = RunningTimer(id, typeId, taskId, startedAt, comment)
internal fun RunningTimer.toEntity() = RunningTimerEntity(id, typeId, taskId, startedAt, comment)
internal fun RecordEntity.toDomain() = TimeRecord(id, typeId, taskId, startAt, endAt, comment, isManual)
internal fun TimeRecord.toEntity() = RecordEntity(id, typeId, taskId, startAt, endAt, comment, isManual)

class ActivityTypeRepositoryImpl @Inject constructor(
    private val dao: ActivityTypeDao,
) : ActivityTypeRepository {
    override fun observeTypes(): Flow<List<ActivityType>> = dao.observeTypes().map { it.map(ActivityTypeEntity::toDomain) }
    override suspend fun getTypes(): List<ActivityType> = dao.getTypes().map { it.toDomain() }
    override suspend fun getType(id: Long): ActivityType? = dao.getType(id)?.toDomain()
    override suspend fun insertType(type: ActivityType): Long = dao.insertType(type.copy(id = 0).toEntity())
    override suspend fun updateType(type: ActivityType) = dao.updateType(type.toEntity())
    override suspend fun deleteType(id: Long) = dao.deleteType(id)
    override suspend fun nextSortOrder(): Long = dao.nextSortOrder()

    override fun observeGoals(): Flow<List<ActivityGoal>> = dao.observeGoals().map { it.map(ActivityGoalEntity::toDomain) }
    override suspend fun getGoals(): List<ActivityGoal> = dao.getGoals().map { it.toDomain() }
    override suspend fun setGoals(typeId: Long, goals: List<ActivityGoal>) =
        dao.replaceGoals(typeId, goals.map { it.copy(typeId = typeId).toEntity() })

    override suspend fun markGoalReached(typeId: Long, period: GoalPeriod, periodStartDay: Long, at: Long): Boolean =
        dao.insertGoalReached(GoalReachedEntity(typeId, period.value, periodStartDay, at)) != -1L
}

class TimeRecordRepositoryImpl @Inject constructor(
    private val dao: TimeRecordDao,
) : TimeRecordRepository {
    override fun observeRunning(): Flow<List<RunningTimer>> = dao.observeRunning().map { it.map(RunningTimerEntity::toDomain) }
    override suspend fun getRunning(): List<RunningTimer> = dao.getRunning().map { it.toDomain() }
    override suspend fun getRunning(id: Long): RunningTimer? = dao.getRunning(id)?.toDomain()
    override suspend fun getRunningByType(typeId: Long): RunningTimer? = dao.getRunningByType(typeId)?.toDomain()
    override suspend fun insertRunning(timer: RunningTimer): Long = dao.insertRunning(timer.copy(id = 0).toEntity())
    override suspend fun updateRunning(timer: RunningTimer) = dao.updateRunning(timer.toEntity())
    override suspend fun deleteRunning(id: Long) = dao.deleteRunning(id)

    override fun observeRecords(from: Long, to: Long): Flow<List<TimeRecord>> =
        dao.observeRecords(from, to).map { it.map(RecordEntity::toDomain) }

    override suspend fun getRecords(from: Long, to: Long, typeId: Long?): List<TimeRecord> =
        (if (typeId == null) dao.getRecords(from, to) else dao.getRecordsOfType(from, to, typeId)).map { it.toDomain() }

    override suspend fun getRecord(id: Long): TimeRecord? = dao.getRecord(id)?.toDomain()
    override suspend fun insertRecord(record: TimeRecord): Long = dao.insertRecord(record.copy(id = 0).toEntity())
    override suspend fun updateRecord(record: TimeRecord) = dao.updateRecord(record.toEntity())
    override suspend fun deleteRecord(id: Long) = dao.deleteRecord(id)
    override fun observeTaskTotalMs(taskId: Long): Flow<Long> = dao.observeTaskTotalMs(taskId)
    override suspend fun lastTypeForTask(taskId: Long): Long? = dao.lastTypeForTask(taskId)
}

class RoomTransactionRunner @Inject constructor(
    private val db: MeoDatabase,
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
