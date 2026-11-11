package com.meolist.domain.testing

import com.meolist.domain.farm.AchievementState
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.FarmField
import com.meolist.domain.farm.FarmState
import com.meolist.domain.farm.LegacyFarmSummary
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
    val fields = MutableStateFlow<List<FarmField>>(emptyList())
    val state = MutableStateFlow<FarmState?>(null)
    val legacy = MutableStateFlow<LegacyFarmSummary?>(null)
    override fun observeFields(): Flow<List<FarmField>> = fields
    override fun observeFarmState(): Flow<FarmState?> = state
    override fun observeLegacyFarmSummary(): Flow<LegacyFarmSummary?> = legacy
    override suspend fun getFields(): List<FarmField> = fields.value
    override suspend fun getFarmState(): FarmState? = state.value
    override suspend fun getLegacyFarmSummary(): LegacyFarmSummary? = legacy.value
    override suspend fun saveField(field: FarmField) {
        fields.value = (fields.value.filterNot { it.slot == field.slot } + field).sortedBy { it.slot }
    }
    override suspend fun initialize(initialSources: List<NutrientEntry>, at: Long): Boolean {
        if (state.value != null) return false
        if (plots.value.isNotEmpty() || nutrients.value.isNotEmpty() || harvests.value.isNotEmpty() || unlocked.value.isNotEmpty()) {
            legacy.value = LegacyFarmSummary(at, plots.value.size, harvests.value.size, unlocked.value.size)
        }
        plots.value = emptyList()
        harvests.value = emptyList()
        unlocked.value = emptyMap()
        nutrients.value = initialSources.sortedBy { it.sourceKey }.mapIndexed { index, entry -> entry.copy(plotId = null, sequence = index + 1L) }
        fields.value = (0..2).map { FarmField(it, 0) }
        state.value = FarmState(2, nutrients.value.size.toLong())
        return true
    }
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

    override suspend fun saveNutrients(entries: List<NutrientEntry>, nextSequence: Long) {
        nutrients.value = entries
        state.value = state.value!!.copy(nextSequence = nextSequence)
    }

    override suspend fun unlockAchievement(key: String, at: Long): Boolean {
        if (key in unlocked.value) return false
        unlocked.value = unlocked.value + (key to at)
        return true
    }

    override suspend fun getUnlockedAchievements(): Map<String, Long> = unlocked.value
}
