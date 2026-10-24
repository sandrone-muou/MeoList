package com.meolist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.meolist.data.db.entity.CountdownEntity
import com.meolist.data.db.entity.HabitCheckInEntity
import com.meolist.data.db.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit ORDER BY sortOrder, id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM habit")
    suspend fun nextSortOrder(): Long

    @Query("SELECT * FROM habit_checkin ORDER BY day DESC")
    fun observeCheckIns(): Flow<List<HabitCheckInEntity>>

    @Query("SELECT * FROM habit_checkin WHERE habitId = :habitId ORDER BY day DESC")
    suspend fun getCheckIns(habitId: Long): List<HabitCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckIn(checkIn: HabitCheckInEntity)

    @Query("DELETE FROM habit_checkin WHERE habitId = :habitId AND day = :day")
    suspend fun deleteCheckIn(habitId: Long, day: Long)
}

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdown ORDER BY pinned DESC, sortOrder, id")
    fun observeAll(): Flow<List<CountdownEntity>>

    @Query("SELECT * FROM countdown WHERE id = :id")
    suspend fun get(id: Long): CountdownEntity?

    @Insert
    suspend fun insert(countdown: CountdownEntity): Long

    @Update
    suspend fun update(countdown: CountdownEntity)

    @Query("DELETE FROM countdown WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM countdown")
    suspend fun nextSortOrder(): Long
}
