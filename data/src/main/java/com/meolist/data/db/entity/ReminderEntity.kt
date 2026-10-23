package com.meolist.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A reminder of a task, habit or countdown. The owner is polymorphic, so there is no foreign key;
 * orphans are removed explicitly after permanent deletes.
 */
@Entity(
    tableName = "reminder",
    indices = [Index("ownerType", "ownerId"), Index("nextFireAt"), Index("snoozedUntil")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: Int,
    val ownerId: Long,
    val kind: Int,
    val offsetMin: Int,
    val minuteOfDay: Int?,
    val nextFireAt: Long?,
    val snoozedUntil: Long?,
    val enabled: Boolean,
)
