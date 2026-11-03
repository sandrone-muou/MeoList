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
import com.meolist.data.db.entity.FarmSlotEntity
import com.meolist.data.db.entity.FarmStateEntity
import com.meolist.data.db.entity.LegacyFarmArchiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmDao {
    @Query("SELECT * FROM farm_state WHERE id = 1")
    suspend fun getFarmState(): FarmStateEntity?

    @Query("SELECT * FROM farm_state WHERE id = 1")
    fun observeFarmState(): Flow<FarmStateEntity?>

    @Upsert
    suspend fun saveFarmState(state: FarmStateEntity)

    @Query("SELECT * FROM farm_slot ORDER BY slot")
    suspend fun getFields(): List<FarmSlotEntity>

    @Query("SELECT * FROM farm_slot ORDER BY slot")
    fun observeFields(): Flow<List<FarmSlotEntity>>

    @Upsert
    suspend fun saveField(field: FarmSlotEntity)

    @Query("SELECT * FROM farm_legacy_archive WHERE id = 1")
    suspend fun getLegacyArchive(): LegacyFarmArchiveEntity?

    @Query("SELECT * FROM farm_legacy_archive WHERE id = 1")
    fun observeLegacyArchive(): Flow<LegacyFarmArchiveEntity?>

    @Upsert
    suspend fun saveLegacyArchive(archive: LegacyFarmArchiveEntity)

    @Query("SELECT * FROM farm_plot ORDER BY slot, plantedAt")
    fun observePlots(): Flow<List<FarmPlotEntity>>

    @Query("SELECT * FROM farm_plot ORDER BY slot, plantedAt")
    suspend fun getPlots(): List<FarmPlotEntity>

    @Insert
    suspend fun insertPlot(plot: FarmPlotEntity): Long

    @Update
    suspend fun updatePlot(plot: FarmPlotEntity)

    @Query("SELECT * FROM farm_nutrient ORDER BY sequence")
    fun observeNutrients(): Flow<List<NutrientEntity>>

    @Query("SELECT * FROM farm_nutrient ORDER BY sequence")
    suspend fun getNutrients(): List<NutrientEntity>

    @Upsert
    suspend fun upsertNutrients(entries: List<NutrientEntity>)

    @Query("DELETE FROM farm_nutrient")
    suspend fun clearNutrients()

    @Query("DELETE FROM farm_plot")
    suspend fun clearPlots()

    @Query("DELETE FROM farm_harvest")
    suspend fun clearHarvests()

    @Query("DELETE FROM farm_achievement")
    suspend fun clearAchievements()

    @Query("DELETE FROM farm_slot")
    suspend fun clearFields()

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
