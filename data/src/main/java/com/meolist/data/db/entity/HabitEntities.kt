package com.meolist.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habit", indices = [Index("archived")])
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    /** Encoded [com.meolist.domain.habit.HabitSchedule]. */
    val scheduleRule: String,
    val dailyTarget: Int,
    val unit: String,
    /** Epoch day. */
    val startDay: Long,
    val archived: Boolean,
    val sortOrder: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "habit_checkin",
    primaryKeys = ["habitId", "day"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId"), Index("day")],
)
data class HabitCheckInEntity(
    val habitId: Long,
    /** Epoch day. */
    val day: Long,
    val value: Int,
)

@Entity(tableName = "countdown", indices = [Index("archived")])
data class CountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Epoch day of the anniversary or milestone. */
    val baseDay: Long,
    val mode: Int,
    val yearly: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val color: Int,
    val note: String,
    val sortOrder: Long,
    val createdAt: Long,
)
