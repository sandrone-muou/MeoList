package com.meolist.domain.farm

import kotlinx.coroutines.flow.Flow

interface FarmRepository {
    fun observePlots(): Flow<List<FarmPlot>>
    fun observeNutrients(): Flow<List<NutrientEntry>>
    fun observeHarvests(): Flow<List<HarvestRecord>>
    fun observeAchievements(): Flow<List<AchievementState>>

    suspend fun getPlots(): List<FarmPlot>
    suspend fun getNutrients(): List<NutrientEntry>
    suspend fun getHarvests(): List<HarvestRecord>

    suspend fun insertPlot(plot: FarmPlot): Long
    suspend fun updatePlot(plot: FarmPlot)

    suspend fun insertHarvest(harvest: HarvestRecord): Long

    /**
     * Replaces the ledger: [entries] are written (existing rows updated), and the rows whose source
     * keys are listed in [removedKeys] are deleted. Rows are assigned to plots, so callers keep the
     * plot id of entries they carry over.
     */
    suspend fun saveNutrients(entries: List<NutrientEntry>, removedKeys: List<String>)

    /** Moves every entry still in the compost pool onto [plotId]. Returns the nutrients moved. */
    suspend fun attachCompost(plotId: Long): Int

    /** Remembers an unlocked badge. Returns false when it was already unlocked. */
    suspend fun unlockAchievement(key: String, at: Long): Boolean
    suspend fun getUnlockedAchievements(): Map<String, Long>
}
