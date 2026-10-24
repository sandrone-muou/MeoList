package com.meolist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.meolist.data.db.entity.ActivityGoalEntity
import com.meolist.data.db.entity.ActivityTypeEntity
import com.meolist.data.db.entity.GoalReachedEntity
import com.meolist.data.db.entity.RecordEntity
import com.meolist.data.db.entity.RunningTimerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityTypeDao {
    @Query("SELECT * FROM activity_type ORDER BY sortOrder, id")
    fun observeTypes(): Flow<List<ActivityTypeEntity>>

    @Query("SELECT * FROM activity_type ORDER BY sortOrder, id")
    suspend fun getTypes(): List<ActivityTypeEntity>

    @Query("SELECT * FROM activity_type WHERE id = :id")
    suspend fun getType(id: Long): ActivityTypeEntity?

    @Insert
    suspend fun insertType(type: ActivityTypeEntity): Long

    @Insert
    suspend fun insertTypes(types: List<ActivityTypeEntity>)

    @Update
    suspend fun updateType(type: ActivityTypeEntity)

    @Query("DELETE FROM activity_type WHERE id = :id")
    suspend fun deleteType(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM activity_type")
    suspend fun nextSortOrder(): Long

    @Query("SELECT * FROM activity_goal")
    fun observeGoals(): Flow<List<ActivityGoalEntity>>

    @Query("SELECT * FROM activity_goal")
    suspend fun getGoals(): List<ActivityGoalEntity>

    @Query("DELETE FROM activity_goal WHERE typeId = :typeId")
    suspend fun deleteGoals(typeId: Long)

    @Insert
    suspend fun insertGoals(goals: List<ActivityGoalEntity>)

    @Transaction
    suspend fun replaceGoals(typeId: Long, goals: List<ActivityGoalEntity>) {
        deleteGoals(typeId)
        insertGoals(goals)
    }

    /** Returns -1 when the row already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGoalReached(row: GoalReachedEntity): Long

    @Query("SELECT * FROM goal_reached ORDER BY reachedAt")
    suspend fun getGoalsReached(): List<GoalReachedEntity>

    @Query("SELECT * FROM goal_reached ORDER BY reachedAt")
    fun observeGoalsReached(): Flow<List<GoalReachedEntity>>
}

@Dao
interface TimeRecordDao {
    @Query("SELECT * FROM running_timer ORDER BY startedAt")
    fun observeRunning(): Flow<List<RunningTimerEntity>>

    @Query("SELECT * FROM running_timer ORDER BY startedAt")
    suspend fun getRunning(): List<RunningTimerEntity>

    @Query("SELECT * FROM running_timer WHERE id = :id")
    suspend fun getRunning(id: Long): RunningTimerEntity?

    @Query("SELECT * FROM running_timer WHERE typeId = :typeId")
    suspend fun getRunningByType(typeId: Long): RunningTimerEntity?

    @Insert
    suspend fun insertRunning(timer: RunningTimerEntity): Long

    @Update
    suspend fun updateRunning(timer: RunningTimerEntity)

    @Query("DELETE FROM running_timer WHERE id = :id")
    suspend fun deleteRunning(id: Long)

    @Query("SELECT * FROM record WHERE startAt < :to AND endAt > :from ORDER BY startAt DESC")
    fun observeRecords(from: Long, to: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM record ORDER BY startAt")
    fun observeAllRecords(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM record ORDER BY startAt")
    suspend fun getAllRecords(): List<RecordEntity>

    @Query("SELECT * FROM record WHERE startAt < :to AND endAt > :from ORDER BY startAt")
    suspend fun getRecords(from: Long, to: Long): List<RecordEntity>

    @Query("SELECT * FROM record WHERE typeId = :typeId AND startAt < :to AND endAt > :from ORDER BY startAt")
    suspend fun getRecordsOfType(from: Long, to: Long, typeId: Long): List<RecordEntity>

    @Query("SELECT * FROM record WHERE id = :id")
    suspend fun getRecord(id: Long): RecordEntity?

    @Insert
    suspend fun insertRecord(record: RecordEntity): Long

    @Insert
    suspend fun insertRecords(records: List<RecordEntity>)

    @Insert
    suspend fun insertRunningTimers(timers: List<RunningTimerEntity>)

    @Update
    suspend fun updateRecord(record: RecordEntity)

    @Query("DELETE FROM record WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("SELECT COALESCE(SUM(endAt - startAt), 0) FROM record WHERE taskId = :taskId")
    fun observeTaskTotalMs(taskId: Long): Flow<Long>

    @Query("SELECT typeId FROM record WHERE taskId = :taskId ORDER BY endAt DESC LIMIT 1")
    suspend fun lastTypeForTask(taskId: Long): Long?
}
