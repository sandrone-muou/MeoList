package com.meolist.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.meolist.data.db.entity.AchievementEntity
import com.meolist.data.db.entity.FarmPlotEntity
import com.meolist.data.db.entity.HarvestEntity
import com.meolist.data.db.entity.NutrientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmDao {
    @Query("SELECT * FROM farm_plot ORDER BY slot, plantedAt")
    fun observePlots(): Flow<List<FarmPlotEntity>>

    @Query("SELECT * FROM farm_plot ORDER BY slot, plantedAt")
    suspend fun getPlots(): List<FarmPlotEntity>

    @Insert
    suspend fun insertPlot(plot: FarmPlotEntity): Long

    @Update
    suspend fun updatePlot(plot: FarmPlotEntity)

    @Query("SELECT * FROM farm_nutrient ORDER BY earnedAt")
    fun observeNutrients(): Flow<List<NutrientEntity>>

    @Query("SELECT * FROM farm_nutrient ORDER BY earnedAt")
    suspend fun getNutrients(): List<NutrientEntity>

    @Upsert
    suspend fun upsertNutrients(entries: List<NutrientEntity>)

    @Query("DELETE FROM farm_nutrient WHERE sourceKey IN (:keys)")
    suspend fun deleteNutrients(keys: List<String>)

    /** Returns the number of rows moved. */
    @Query("UPDATE farm_nutrient SET plotId = :plotId WHERE plotId IS NULL")
    suspend fun attachCompost(plotId: Long): Int

    @Query("SELECT * FROM farm_harvest ORDER BY harvestedAt DESC")
    fun observeHarvests(): Flow<List<HarvestEntity>>

    @Query("SELECT * FROM farm_harvest ORDER BY harvestedAt DESC")
    suspend fun getHarvests(): List<HarvestEntity>

    @Insert
    suspend fun insertHarvest(harvest: HarvestEntity): Long

    @Query("SELECT * FROM farm_achievement")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM farm_achievement")
    suspend fun getAchievements(): List<AchievementEntity>

    /** Returns -1 when the badge was already unlocked. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: AchievementEntity): Long
}
