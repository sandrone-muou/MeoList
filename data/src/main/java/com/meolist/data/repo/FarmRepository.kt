package com.meolist.data.repo

import com.meolist.data.db.dao.FarmDao
import com.meolist.data.db.entity.AchievementEntity
import com.meolist.data.db.entity.FarmPlotEntity
import com.meolist.data.db.entity.HarvestEntity
import com.meolist.data.db.entity.NutrientEntity
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.AchievementState
import com.meolist.domain.farm.Crop
import com.meolist.domain.farm.FarmPlot
import com.meolist.domain.farm.FarmRepository
import com.meolist.domain.farm.HarvestRecord
import com.meolist.domain.farm.NutrientEntry
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FarmRepositoryImpl @Inject constructor(private val dao: FarmDao) : FarmRepository {
    override fun observePlots(): Flow<List<FarmPlot>> = dao.observePlots().map { list -> list.map { it.toModel() } }

    override fun observeNutrients(): Flow<List<NutrientEntry>> =
        dao.observeNutrients().map { list -> list.map { it.toModel() } }

    override fun observeHarvests(): Flow<List<HarvestRecord>> =
        dao.observeHarvests().map { list -> list.map { it.toModel() } }

    override fun observeAchievements(): Flow<List<AchievementState>> =
        dao.observeAchievements().map { list -> list.mapNotNull { it.toState() } }

    override suspend fun getPlots(): List<FarmPlot> = dao.getPlots().map { it.toModel() }

    override suspend fun getNutrients(): List<NutrientEntry> = dao.getNutrients().map { it.toModel() }

    override suspend fun getHarvests(): List<HarvestRecord> = dao.getHarvests().map { it.toModel() }

    override suspend fun insertPlot(plot: FarmPlot): Long = dao.insertPlot(plot.toEntity())

    override suspend fun updatePlot(plot: FarmPlot) = dao.updatePlot(plot.toEntity())

    override suspend fun insertHarvest(harvest: HarvestRecord): Long = dao.insertHarvest(harvest.toEntity())

    override suspend fun saveNutrients(entries: List<NutrientEntry>, removedKeys: List<String>) {
        removedKeys.chunked(500).forEach { chunk -> dao.deleteNutrients(chunk) }
        if (entries.isNotEmpty()) dao.upsertNutrients(entries.map { it.toEntity() })
    }

    override suspend fun attachCompost(plotId: Long): Int {
        val pool = dao.getNutrients().filter { it.plotId == null }
        if (pool.isEmpty()) return 0
        dao.attachCompost(plotId)
        return pool.sumOf { it.amount }
    }

    override suspend fun unlockAchievement(key: String, at: Long): Boolean =
        dao.insertAchievement(AchievementEntity(key, at)) != -1L

    override suspend fun getUnlockedAchievements(): Map<String, Long> =
        dao.getAchievements().associate { it.key to it.unlockedAt }

    private fun FarmPlotEntity.toModel() = FarmPlot(
        id = id,
        slot = slot,
        crop = Crop.fromKey(crop) ?: Crop.CARROT,
        plantedAt = plantedAt,
        harvestedAt = harvestedAt,
    )

    private fun FarmPlot.toEntity() = FarmPlotEntity(
        id = id,
        slot = slot,
        crop = crop.key,
        plantedAt = plantedAt,
        harvestedAt = harvestedAt,
    )

    private fun NutrientEntity.toModel() = NutrientEntry(sourceKey, plotId, amount, earnedAt)

    private fun NutrientEntry.toEntity() = NutrientEntity(sourceKey, plotId, amount, earnedAt)

    private fun HarvestEntity.toModel() = HarvestRecord(
        id = id,
        crop = Crop.fromKey(crop) ?: Crop.CARROT,
        plotId = plotId,
        harvestedAt = harvestedAt,
        nutrients = nutrients,
    )

    private fun HarvestRecord.toEntity() = HarvestEntity(
        id = id,
        crop = crop.key,
        plotId = plotId,
        harvestedAt = harvestedAt,
        nutrients = nutrients,
    )

    private fun AchievementEntity.toState(): AchievementState? {
        val achievement = Achievement.fromKey(key) ?: return null
        return AchievementState(achievement, achievement.target, unlockedAt)
    }
}
