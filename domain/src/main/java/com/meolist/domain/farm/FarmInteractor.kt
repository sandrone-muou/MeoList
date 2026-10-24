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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface PlantResult {
    data class Planted(val plotId: Long, val compostUsed: Int) : PlantResult
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
 * The farm ledger. Every finished timing, completed task, habit check-in and reached goal is worth
 * nutrients; the ledger is rebuilt from those sources whenever one of them changes. Deleting or
 * editing a record therefore removes the corresponding nutrients and rolls the plot back, while
 * harvested crops and unlocked achievements stay.
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
    suspend fun sync() = mutex.withLock {
        val sources = loadSources()
        val settings = prefs.settings.first()
        val now = clock.now()
        reconcile(sources)
        evaluateAchievements(sources, now, settings.firstDayOfWeek)
    }

    /** Live farm data for the screen. */
    fun observe(): Flow<FarmSnapshot> {
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
        return combine(farmData, sources, prefs.settings) { data, src, settings ->
            val metrics = FarmRules.metrics(
                records = src.records,
                tasks = src.tasks,
                habits = src.habits,
                checkIns = src.checkIns,
                goals = src.goals,
                harvests = data.harvests,
                today = clock.today(),
                firstDayOfWeek = settings.firstDayOfWeek,
            )
            FarmSnapshot(
                plots = data.plots,
                entries = data.entries,
                harvests = data.harvests,
                achievements = FarmRules.achievementStates(
                    metrics,
                    data.achievements.filter { it.unlockedAt != null }
                        .associate { it.achievement.key to it.unlockedAt!! },
                ),
                metrics = metrics,
            )
        }
    }

    /** Plants [crop] on an unlocked, empty slot, optionally pulling the compost pool into it. */
    suspend fun plant(slot: Int, crop: Crop, useCompost: Boolean = false): PlantResult = mutex.withLock {
        val plots = farm.getPlots()
        if (plots.any { it.harvestedAt == null && it.slot == slot }) return@withLock PlantResult.SlotTaken
        val limit = FarmRules.plotLimit(farm.getHarvests().size)
        if (slot !in 0 until limit) return@withLock PlantResult.SlotLocked
        val sources = loadSources()
        if (!FarmRules.isCropUnlocked(crop, metricsOf(sources, farm.getHarvests()))) {
            return@withLock PlantResult.CropLocked
        }
        val (id, used) = tx.run {
            val id = farm.insertPlot(FarmPlot(slot = slot, crop = crop, plantedAt = clock.now()))
            id to (if (useCompost) farm.attachCompost(id) else 0)
        }
        return@withLock PlantResult.Planted(id, used)
    }

    /** Harvests a ripe plot. The nutrients stay on the plot as history; the slot becomes free. */
    suspend fun harvest(plotId: Long): HarvestResult = mutex.withLock {
        val plot = farm.getPlots().firstOrNull { it.id == plotId && it.harvestedAt == null }
            ?: return@withLock HarvestResult.Empty
        val entries = farm.getNutrients()
        val growth = FarmRules.growth(plot, entries)
        if (growth < plot.crop.need) return@withLock HarvestResult.NotReady
        val now = clock.now()
        val stored = HarvestRecord(
            crop = plot.crop,
            plotId = plot.id,
            harvestedAt = now,
            nutrients = growth,
        )
        val id = tx.run {
            farm.updatePlot(plot.copy(harvestedAt = now))
            farm.insertHarvest(stored)
        }
        // More harvests may unlock slots, crops and badges right away.
        val sources = loadSources()
        evaluateAchievements(sources, now, prefs.settings.first().firstDayOfWeek)
        HarvestResult.Harvested(stored.copy(id = id))
    }

    /** Moves the whole compost pool into a growing plot. Returns the nutrients moved. */
    suspend fun applyCompost(plotId: Long): Int = mutex.withLock {
        val plot = farm.getPlots().firstOrNull { it.id == plotId && it.harvestedAt == null } ?: return@withLock 0
        farm.attachCompost(plot.id)
    }

    private suspend fun reconcile(sources: FarmSources) {
        val existing = farm.getNutrients()
        val byKey = existing.associateBy { it.sourceKey }
        val desired = FarmRules.sourceNutrients(sources, clock.zone())
        val removed = existing.map { it.sourceKey }.filter { it !in desired }
        val kept = desired.mapNotNull { (key, amount) ->
            byKey[key]?.copy(amount = amount.amount, earnedAt = amount.earnedAt)
        }
        val plots = farm.getPlots().filter { it.harvestedAt == null }.sortedBy { it.slot }
        val sums = mutableMapOf<Long, Int>()
        kept.forEach { entry -> entry.plotId?.let { sums[it] = (sums[it] ?: 0) + entry.amount } }

        val added = desired
            .filterKeys { it !in byKey }
            .map { (key, amount) -> NutrientEntry(key, null, amount.amount, amount.earnedAt) }
            .sortedWith(compareBy({ it.earnedAt }, { it.sourceKey }))
        val final = kept.toMutableList()
        added.forEach { entry ->
            // Send new nutrients to the earliest ripening plot; anything beyond goes to the compost.
            val target = plots.firstOrNull { (sums[it.id] ?: 0) < it.crop.need }
            val assigned = entry.copy(plotId = target?.id)
            if (target != null) sums[target.id] = (sums[target.id] ?: 0) + entry.amount
            final += assigned
        }
        if (removed.isEmpty() && final.size == existing.size && final.all { byKey[it.sourceKey] == it }) return
        tx.run { farm.saveNutrients(final, removed) }
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
}
