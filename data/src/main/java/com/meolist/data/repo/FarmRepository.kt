package com.meolist.data.repo

import com.meolist.data.backup.AchievementDto
import com.meolist.data.backup.BackupCodec
import com.meolist.data.backup.FarmPlotDto
import com.meolist.data.backup.HarvestDto
import com.meolist.data.backup.LegacyFarmArchiveDto
import com.meolist.data.backup.NutrientDto
import com.meolist.data.db.dao.FarmDao
import com.meolist.data.db.entity.AchievementEntity
import com.meolist.data.db.entity.FarmPlotEntity
import com.meolist.data.db.entity.FarmSlotEntity
import com.meolist.data.db.entity.FarmStateEntity
import com.meolist.data.db.entity.HarvestEntity
import com.meolist.data.db.entity.LegacyFarmArchiveEntity
import com.meolist.data.db.entity.NutrientEntity
import com.meolist.domain.farm.Achievement
import com.meolist.domain.farm.AchievementState
import com.meolist.domain.farm.Crop
import com.meolist.domain.farm.FarmField
import com.meolist.domain.farm.FarmPlot
import com.meolist.domain.farm.FarmRepository
import com.meolist.domain.farm.FarmState
import com.meolist.domain.farm.HarvestRecord
import com.meolist.domain.farm.LegacyFarmSummary
import com.meolist.domain.farm.NutrientEntry
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FarmRepositoryImpl @Inject constructor(private val dao: FarmDao) : FarmRepository {
    override fun observeFields(): Flow<List<FarmField>> = dao.observeFields().map { fields ->
        fields.map { FarmField(it.slot, it.sourceCutoff) }
    }

    override fun observeFarmState(): Flow<FarmState?> = dao.observeFarmState().map { it?.toModel() }

    override fun observeLegacyFarmSummary(): Flow<LegacyFarmSummary?> =
        dao.observeLegacyArchive().map { it?.toSummary() }

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

    override suspend fun getFields(): List<FarmField> = dao.getFields().map { FarmField(it.slot, it.sourceCutoff) }

    override suspend fun getFarmState(): FarmState? = dao.getFarmState()?.toModel()

    override suspend fun getLegacyFarmSummary(): LegacyFarmSummary? = dao.getLegacyArchive()?.toSummary()

    override suspend fun initialize(initialSources: List<NutrientEntry>, at: Long): Boolean {
        if (dao.getFarmState() != null) return false

        val oldPlots = dao.getPlots()
        val oldNutrients = dao.getNutrients()
        val oldHarvests = dao.getHarvests()
        val oldAchievements = dao.getAchievements()
        if (oldPlots.isNotEmpty() || oldNutrients.isNotEmpty() || oldHarvests.isNotEmpty() || oldAchievements.isNotEmpty()) {
            val archive = LegacyFarmArchiveDto(
                archivedAt = at,
                plots = oldPlots.map { FarmPlotDto(it.id, it.slot, it.crop, it.plantedAt, it.harvestedAt) },
                nutrients = oldNutrients.map { NutrientDto(it.sourceKey, it.plotId, it.amount, it.earnedAt, it.sequence) },
                harvests = oldHarvests.map { HarvestDto(it.id, it.crop, it.plotId, it.harvestedAt, it.nutrients) },
                achievements = oldAchievements.map { AchievementDto(it.key, it.unlockedAt) },
            )
            dao.saveLegacyArchive(
                LegacyFarmArchiveEntity(
                    archivedAt = at,
                    plotCount = oldPlots.size,
                    harvestCount = oldHarvests.size,
                    achievementCount = oldAchievements.size,
                    payload = BackupCodec.encodeLegacyFarm(archive),
                ),
            )
        }

        dao.clearNutrients()
        dao.clearPlots()
        dao.clearHarvests()
        dao.clearAchievements()
        dao.clearFields()
        val seeded = initialSources.sortedBy { it.sourceKey }.mapIndexed { index, entry ->
            entry.copy(plotId = null, sequence = index + 1L)
        }
        if (seeded.isNotEmpty()) dao.upsertNutrients(seeded.map { it.toEntity() })
        repeat(3) { dao.saveField(FarmSlotEntity(slot = it, sourceCutoff = 0)) }
        dao.saveFarmState(FarmStateEntity(rulesVersion = RULES_VERSION, nextSequence = seeded.size.toLong()))
        return true
    }

    override suspend fun insertPlot(plot: FarmPlot): Long = dao.insertPlot(plot.toEntity())

    override suspend fun updatePlot(plot: FarmPlot) = dao.updatePlot(plot.toEntity())

    override suspend fun insertHarvest(harvest: HarvestRecord): Long = dao.insertHarvest(harvest.toEntity())

    override suspend fun saveNutrients(entries: List<NutrientEntry>, nextSequence: Long) {
        if (entries.isNotEmpty()) dao.upsertNutrients(entries.map { it.toEntity() })
        dao.saveFarmState(FarmStateEntity(rulesVersion = RULES_VERSION, nextSequence = nextSequence))
    }

    override suspend fun saveField(field: FarmField) = dao.saveField(FarmSlotEntity(field.slot, field.sourceCutoff))

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

    private fun NutrientEntity.toModel() = NutrientEntry(sourceKey, plotId, amount, earnedAt, sequence)

    private fun NutrientEntry.toEntity() = NutrientEntity(sourceKey, plotId, amount, earnedAt, sequence)

    private fun FarmStateEntity.toModel() = FarmState(rulesVersion, nextSequence)

    private fun LegacyFarmArchiveEntity.toSummary() =
        LegacyFarmSummary(archivedAt, plotCount, harvestCount, achievementCount)

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

    private companion object {
        const val RULES_VERSION = 2
    }
}
