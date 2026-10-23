package com.meolist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.meolist.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY id")
    fun observeForOwner(ownerType: Int, ownerId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT DISTINCT ownerId FROM reminder WHERE ownerType = :ownerType AND enabled")
    fun observeOwnerIds(ownerType: Int): Flow<List<Long>>

    @Query("SELECT * FROM reminder WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY id")
    suspend fun getForOwner(ownerType: Int, ownerId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminder")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE enabled AND COALESCE(snoozedUntil, nextFireAt) <= :at")
    suspend fun getDue(at: Long): List<ReminderEntity>

    @Query("SELECT MIN(COALESCE(snoozedUntil, nextFireAt)) FROM reminder WHERE enabled")
    suspend fun nextFireTime(): Long?

    @Query("DELETE FROM reminder WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: Int, ownerId: Long)

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Update
    suspend fun updateAll(reminders: List<ReminderEntity>)

    @Transaction
    suspend fun replaceForOwner(ownerType: Int, ownerId: Long, reminders: List<ReminderEntity>) {
        deleteForOwner(ownerType, ownerId)
        insertAll(reminders)
    }

    @Query("DELETE FROM reminder WHERE ownerType = 0 AND ownerId NOT IN (SELECT id FROM task)")
    suspend fun deleteTaskOrphans()
}
