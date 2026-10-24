package com.meolist.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A patch of soil. Harvested plots stay as history; the next planting adds a new row. */
@Entity(tableName = "farm_plot", indices = [Index("slot"), Index("harvestedAt")])
data class FarmPlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slot: Int,
    /** [com.meolist.domain.farm.Crop.key]. */
    val crop: String,
    val plantedAt: Long,
    val harvestedAt: Long?,
)

/** The nutrient ledger. The source key makes rebuilding it from records/tasks/habits idempotent. */
@Entity(tableName = "farm_nutrient", indices = [Index("plotId"), Index("earnedAt")])
data class NutrientEntity(
    @PrimaryKey val sourceKey: String,
    val plotId: Long?,
    val amount: Int,
    val earnedAt: Long,
)

@Entity(tableName = "farm_harvest", indices = [Index("harvestedAt"), Index("crop")])
data class HarvestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val crop: String,
    val plotId: Long,
    val harvestedAt: Long,
    val nutrients: Int,
)

@Entity(tableName = "farm_achievement")
data class AchievementEntity(
    @PrimaryKey val key: String,
    val unlockedAt: Long,
)
