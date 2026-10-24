package com.meolist.domain.testing

import com.meolist.domain.farm.AchievementState
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.FarmPlot
import com.meolist.domain.farm.FarmRepository
import com.meolist.domain.farm.HarvestRecord
import com.meolist.domain.farm.NutrientEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory farm store. */
class FakeFarmRepository : FarmRepository {
    val plots = MutableStateFlow<List<FarmPlot>>(emptyList())
    val nutrients = MutableStateFlow<List<NutrientEntry>>(emptyList())
    val harvests = MutableStateFlow<List<HarvestRecord>>(emptyList())
    val unlocked = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var nextPlotId = 1L
    private var nextHarvestId = 1L

    override fun observePlots(): Flow<List<FarmPlot>> = plots
    override fun observeNutrients(): Flow<List<NutrientEntry>> = nutrients
    override fun observeHarvests(): Flow<List<HarvestRecord>> = harvests
    override fun observeAchievements(): Flow<List<AchievementState>> = unlocked.map { map ->
        Achievement.entries.map { AchievementState(it, it.target, map[it.key]) }
    }

    override suspend fun getPlots(): List<FarmPlot> = plots.value
    override suspend fun getNutrients(): List<NutrientEntry> = nutrients.value
    override suspend fun getHarvests(): List<HarvestRecord> = harvests.value

    override suspend fun insertPlot(plot: FarmPlot): Long {
        val id = nextPlotId++
        plots.value = plots.value + plot.copy(id = id)
        return id
    }

    override suspend fun updatePlot(plot: FarmPlot) {
        plots.value = plots.value.map { if (it.id == plot.id) plot else it }
    }

    override suspend fun insertHarvest(harvest: HarvestRecord): Long {
        val id = nextHarvestId++
        harvests.value = listOf(harvest.copy(id = id)) + harvests.value
        return id
    }

    override suspend fun saveNutrients(entries: List<NutrientEntry>, removedKeys: List<String>) {
        nutrients.value = entries.filter { it.sourceKey !in removedKeys }
    }

    override suspend fun attachCompost(plotId: Long): Int {
        val pool = nutrients.value.filter { it.plotId == null }
        if (pool.isEmpty()) return 0
        nutrients.value = nutrients.value.map { if (it.plotId == null) it.copy(plotId = plotId) else it }
        return pool.sumOf { it.amount }
    }

    override suspend fun unlockAchievement(key: String, at: Long): Boolean {
        if (key in unlocked.value) return false
        unlocked.value = unlocked.value + (key to at)
        return true
    }

    override suspend fun getUnlockedAchievements(): Map<String, Long> = unlocked.value
}
