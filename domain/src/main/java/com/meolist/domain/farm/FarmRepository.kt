package com.meolist.domain.farm

import kotlinx.coroutines.flow.Flow

interface FarmRepository {
    fun observePlots(): Flow<List<FarmPlot>>
    fun observeNutrients(): Flow<List<NutrientEntry>>
    fun observeHarvests(): Flow<List<HarvestRecord>>
    fun observeAchievements(): Flow<List<AchievementState>>
    fun observeFields(): Flow<List<FarmField>>
    fun observeFarmState(): Flow<FarmState?>
    fun observeLegacyFarmSummary(): Flow<LegacyFarmSummary?>

    suspend fun getPlots(): List<FarmPlot>
    suspend fun getNutrients(): List<NutrientEntry>
    suspend fun getHarvests(): List<HarvestRecord>
    suspend fun getFields(): List<FarmField>
    suspend fun getFarmState(): FarmState?
    suspend fun getLegacyFarmSummary(): LegacyFarmSummary?

    /** Initializes new farms or archives and rebuilds a v1 farm atomically on first use. */
    suspend fun initialize(initialSources: List<NutrientEntry>, at: Long): Boolean

    suspend fun insertPlot(plot: FarmPlot): Long
    suspend fun updatePlot(plot: FarmPlot)

    suspend fun insertHarvest(harvest: HarvestRecord): Long

    /** Replaces the source ledger while retaining removed keys as zero-value tombstones. */
    suspend fun saveNutrients(entries: List<NutrientEntry>, nextSequence: Long)

    suspend fun saveField(field: FarmField)

    /** Remembers an unlocked badge. Returns false when it was already unlocked. */
    suspend fun unlockAchievement(key: String, at: Long): Boolean
    suspend fun getUnlockedAchievements(): Map<String, Long>
}
