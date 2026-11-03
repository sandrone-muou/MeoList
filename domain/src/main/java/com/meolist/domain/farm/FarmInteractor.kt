package com.meolist.domain.farm

import com.meolist.domain.common.Clock
import com.meolist.domain.common.TransactionRunner
import com.meolist.domain.common.today
import com.meolist.domain.habit.HabitRepository
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.TimeRecordRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface PlantResult {
    data class Planted(val plotId: Long, val storedNutrients: Long) : PlantResult
    data object SlotTaken : PlantResult
    /** The slot is not unlocked yet (it needs more harvests). */
    data object SlotLocked : PlantResult
    data object CropLocked : PlantResult
}

sealed interface HarvestResult {
    data class Harvested(val record: HarvestRecord) : HarvestResult
    data object Empty : HarvestResult
    data object NotReady : HarvestResult
}

/**
 * Every source awards the same nutrients to every field that is unlocked when the source is first
 * recorded. Each field spends only its own balance when a crop is harvested.
 */
@Singleton
class FarmInteractor @Inject constructor(
    private val farm: FarmRepository,
    private val records: TimeRecordRepository,
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val types: ActivityTypeRepository,
    private val prefs: AppPreferences,
    private val clock: Clock,
    private val tx: TransactionRunner,
) : FarmHook {
    private val mutex = Mutex()

    override suspend fun onNutrientsMayHaveChanged() {
        sync()
    }

    /**
     * Brings the ledger, crops and badges up to date. Safe to call often: it only writes changes and
     * runs under a mutex so the timer sync and the farm screen cannot interleave.
     */
    suspend fun sync() = mutex.withLock { syncLocked() }

    private suspend fun syncLocked() {
        val sources = loadSources()
        val settings = prefs.settings.first()
        val now = clock.now()
        val desired = FarmRules.sourceNutrients(sources, clock.zone())
        tx.run {
            farm.initialize(
                initialSources = desired.toSortedMap().map { (key, amount) ->
                    NutrientEntry(key, null, amount.amount, amount.earnedAt)
                },
                at = now,
            )
        }
        reconcile(sources)
        evaluateAchievements(sources, now, settings.firstDayOfWeek)
    }

    /** Live farm data for the screen. */
    fun observe(): Flow<FarmSnapshot> = flow {
        sync()
        emitAll(observeInitialized())
    }

    private fun observeInitialized(): Flow<FarmSnapshot> {
        val farmData = combine(
            farm.observePlots(),
            farm.observeNutrients(),
            farm.observeHarvests(),
            farm.observeAchievements(),
        ) { plots, entries, harvests, achievements ->
            FarmData(plots, entries, harvests, achievements)
        }
        val sources = combine(
            records.observeAllRecords(),
            tasks.observeAll(),
            habits.observeHabits(),
            habits.observeCheckIns(),
            types.observeReachedGoals(),
        ) { recordList, taskList, habitList, checkIns, goals ->
            FarmSources(recordList, taskList, habitList, checkIns, goals)
        }
        val liveFarm = combine(farmData, farm.observeFields(), farm.observeLegacyFarmSummary()) { data, fields, legacy ->
            FarmLiveData(data, fields, legacy)
        }
        return combine(liveFarm, sources, prefs.settings) { live, src, settings ->
            val metrics = FarmRules.metrics(
                records = src.records,
                tasks = src.tasks,
                habits = src.habits,
                checkIns = src.checkIns,
                goals = src.goals,
                harvests = live.data.harvests,
                today = clock.today(),
                firstDayOfWeek = settings.firstDayOfWeek,
            )
            FarmSnapshot(
                plots = live.data.plots,
                entries = live.data.entries,
                harvests = live.data.harvests,
                achievements = FarmRules.achievementStates(
                    metrics,
                    live.data.achievements.filter { it.unlockedAt != null }
                        .associate { it.achievement.key to it.unlockedAt!! },
                ),
                metrics = metrics,
                fields = live.fields,
                legacyFarm = live.legacy,
            )
        }
    }

    /** Plants [crop] on an unlocked, empty field, using that field's stored balance. */
    suspend fun plant(slot: Int, crop: Crop): PlantResult = mutex.withLock {
        syncLocked()
        val plots = farm.getPlots()
        if (plots.any { it.harvestedAt == null && it.slot == slot }) return@withLock PlantResult.SlotTaken
        val limit = FarmRules.plotLimit(farm.getHarvests().size)
        if (slot !in 0 until limit) return@withLock PlantResult.SlotLocked
        if (farm.getFields().none { it.slot == slot }) return@withLock PlantResult.SlotLocked
        val sources = loadSources()
        if (!FarmRules.isCropUnlocked(crop, metricsOf(sources, farm.getHarvests()))) {
            return@withLock PlantResult.CropLocked
        }
        val id = farm.insertPlot(FarmPlot(slot = slot, crop = crop, plantedAt = clock.now()))
        val sourceCutoff = farm.getFields().first { it.slot == slot }.sourceCutoff
        val stored = farm.getNutrients().asSequence()
            .filter { it.sequence > sourceCutoff }
            .sumOf { it.amount.toLong() }
        val spent = spentForSlot(slot, plots, farm.getHarvests())
        return@withLock PlantResult.Planted(id, (stored - spent).coerceAtLeast(0))
    }

    /** Harvests a ripe plot. The nutrients stay on the plot as history; the slot becomes free. */
    suspend fun harvest(plotId: Long): HarvestResult = mutex.withLock {
        syncLocked()
        val plot = farm.getPlots().firstOrNull { it.id == plotId && it.harvestedAt == null }
            ?: return@withLock HarvestResult.Empty
        val entries = farm.getNutrients()
        val plots = farm.getPlots()
        val harvests = farm.getHarvests()
        val field = farm.getFields().firstOrNull { it.slot == plot.slot }
            ?: return@withLock HarvestResult.Empty
        val growth = FarmRules.growth(
            plot = plot,
            entries = entries,
            sourceCutoff = field.sourceCutoff,
            spent = spentForSlot(plot.slot, plots, harvests),
        )
        if (growth < plot.crop.need) return@withLock HarvestResult.NotReady
        val now = clock.now()
        val stored = HarvestRecord(
            crop = plot.crop,
            plotId = plot.id,
            harvestedAt = now,
            // A crop consumes exactly its requirement; a final entry may overshoot it.
            nutrients = minOf(growth, plot.crop.need),
        )
        val id = tx.run {
            farm.updatePlot(plot.copy(harvestedAt = now))
            val harvestId = farm.insertHarvest(stored)
            val newLimit = FarmRules.plotLimit(harvests.size + 1)
            val fields = farm.getFields()
            if (newLimit > fields.size) {
                val sequence = farm.getFarmState()?.nextSequence ?: 0
                for (slot in fields.size until newLimit) farm.saveField(FarmField(slot, sequence))
            }
            harvestId
        }
        // More harvests may unlock slots, crops and badges right away.
        val sources = loadSources()
        evaluateAchievements(sources, now, prefs.settings.first().firstDayOfWeek)
        HarvestResult.Harvested(stored.copy(id = id))
    }

    private suspend fun reconcile(sources: FarmSources) {
        val existing = farm.getNutrients()
        val byKey = existing.associateBy { it.sourceKey }
        val desired = FarmRules.sourceNutrients(sources, clock.zone())
        val state = farm.getFarmState() ?: return
        var nextSequence = state.nextSequence
        val final = existing.map { entry ->
            val amount = desired[entry.sourceKey]
            entry.copy(amount = amount?.amount ?: 0, earnedAt = amount?.earnedAt ?: entry.earnedAt)
        }.toMutableList()
        desired.toSortedMap().forEach { (key, amount) ->
            if (key !in byKey) {
                nextSequence += 1
                final += NutrientEntry(key, null, amount.amount, amount.earnedAt, nextSequence)
            }
        }
        if (nextSequence == state.nextSequence && final == existing) return
        tx.run { farm.saveNutrients(final, nextSequence) }
    }

    private suspend fun spentForSlot(
        slot: Int,
        plots: List<FarmPlot>,
        harvests: List<HarvestRecord>,
    ): Long {
        val plotIds = plots.asSequence().filter { it.slot == slot }.map { it.id }.toSet()
        return harvests.asSequence().filter { it.plotId in plotIds }.sumOf { it.nutrients.toLong() }
    }

    private suspend fun evaluateAchievements(
        sources: FarmSources,
        now: Long,
        firstDayOfWeek: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY,
    ) {
        val metrics = metricsOf(sources, farm.getHarvests(), firstDayOfWeek)
        val unlocked = farm.getUnlockedAchievements()
        Achievement.entries.forEach { achievement ->
            if (achievement.key !in unlocked && metrics.of(achievement.metric) >= achievement.target) {
                farm.unlockAchievement(achievement.key, now)
            }
        }
    }

    private suspend fun loadSources(): FarmSources = FarmSources(
        records = records.getAllRecords(),
        tasks = tasks.getAll(),
        habits = habits.getHabits(),
        checkIns = habits.getAllCheckIns(),
        goals = types.getReachedGoals(),
    )

    private fun metricsOf(
        sources: FarmSources,
        harvests: List<HarvestRecord>,
        firstDayOfWeek: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY,
    ): FarmMetrics = FarmRules.metrics(
        records = sources.records,
        tasks = sources.tasks,
        habits = sources.habits,
        checkIns = sources.checkIns,
        goals = sources.goals,
        harvests = harvests,
        today = clock.today(),
        firstDayOfWeek = firstDayOfWeek,
    )

    private data class FarmData(
        val plots: List<FarmPlot>,
        val entries: List<NutrientEntry>,
        val harvests: List<HarvestRecord>,
        val achievements: List<AchievementState>,
    )

    private data class FarmLiveData(
        val data: FarmData,
        val fields: List<FarmField>,
        val legacy: LegacyFarmSummary?,
    )
}
