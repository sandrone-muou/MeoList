package com.meolist.domain.farm

import com.meolist.domain.habit.HabitCheckIn
import com.meolist.domain.task.Priority
import com.meolist.domain.task.Task
import com.meolist.domain.tracker.ReachedGoal
import com.meolist.domain.tracker.TimeRecord

/** What has to happen before a crop can be planted. Requirements are checked live, not stored. */
sealed interface CropUnlock {
    data object Always : CropUnlock
    data class Harvests(val count: Int) : CropUnlock
    data class FocusMinutes(val minutes: Int) : CropUnlock
    data class StreakDays(val days: Int) : CropUnlock

    /** Every other crop has been harvested at least once. */
    data object AllOtherCrops : CropUnlock
}

/**
 * A crop the farm can grow. [need] is the amount of nutrients (roughly focus minutes) it takes to
 * ripen; every finished crop yields one harvest.
 */
enum class Crop(val key: String, val need: Int, val unlock: CropUnlock) {
    CARROT("carrot", 180, CropUnlock.Always),
    TOMATO("tomato", 600, CropUnlock.Harvests(3)),
    SUNFLOWER("sunflower", 1500, CropUnlock.FocusMinutes(600)),
    PUMPKIN("pumpkin", 2400, CropUnlock.StreakDays(21)),
    GRAPE("grape", 4000, CropUnlock.FocusMinutes(3000)),
    CHERRY("cherry", 8000, CropUnlock.AllOtherCrops),
    ;

    companion object {
        fun fromKey(key: String): Crop? = entries.firstOrNull { it.key == key }
    }
}

/** A patch of soil. Harvested plots keep their row as history; planting again creates a new one. */
data class FarmPlot(
    val id: Long = 0,
    val slot: Int,
    val crop: Crop,
    val plantedAt: Long,
    val harvestedAt: Long? = null,
) {
    val isHarvested: Boolean get() = harvestedAt != null
}

/**
 * One award of nutrients, keyed by what produced it ("record:12", "task:4", "habit:3:20345",
 * "goal:1:0:20341"). The key makes the ledger idempotent: the farm can be rebuilt from the sources
 * at any time, so editing or deleting a record rolls the growth back.
 */
data class NutrientEntry(
    val sourceKey: String,
    val plotId: Long?,
    val amount: Int,
    val earnedAt: Long,
)

/** A finished crop. Kept for the harvest log and achievements. */
data class HarvestRecord(
    val id: Long = 0,
    val crop: Crop,
    val plotId: Long,
    val harvestedAt: Long,
    val nutrients: Int,
) {
    /** Older rows may exceed the crop's need; the UI shows what the crop could absorb. */
    val absorbedNutrients: Int get() = minOf(nutrients, crop.need)
}

enum class AchievementMetric { HARVESTS, FOCUS_MINUTES, TASKS_DONE, BEST_STREAK, GOALS_REACHED, CROPS_HARVESTED }

/** Permanent badges; once unlocked they stay, even if the underlying records are deleted. */
enum class Achievement(val key: String, val metric: AchievementMetric, val target: Int) {
    FIRST_HARVEST("first_harvest", AchievementMetric.HARVESTS, 1),
    HARVEST_5("harvest_5", AchievementMetric.HARVESTS, 5),
    HARVEST_15("harvest_15", AchievementMetric.HARVESTS, 15),
    FOCUS_1H("focus_1h", AchievementMetric.FOCUS_MINUTES, 60),
    FOCUS_10H("focus_10h", AchievementMetric.FOCUS_MINUTES, 600),
    FOCUS_50H("focus_50h", AchievementMetric.FOCUS_MINUTES, 3000),
    FOCUS_200H("focus_200h", AchievementMetric.FOCUS_MINUTES, 12000),
    TASKS_10("tasks_10", AchievementMetric.TASKS_DONE, 10),
    TASKS_50("tasks_50", AchievementMetric.TASKS_DONE, 50),
    STREAK_7("streak_7", AchievementMetric.BEST_STREAK, 7),
    STREAK_21("streak_21", AchievementMetric.BEST_STREAK, 21),
    GOALS_5("goals_5", AchievementMetric.GOALS_REACHED, 5),
    ALL_CROPS("all_crops", AchievementMetric.CROPS_HARVESTED, Crop.entries.size),
    ;

    companion object {
        fun fromKey(key: String): Achievement? = entries.firstOrNull { it.key == key }
    }
}

/** [unlockedAt] is null while the badge is still being worked on. */
data class AchievementState(
    val achievement: Achievement,
    val progress: Int,
    val unlockedAt: Long? = null,
) {
    val unlocked: Boolean get() = unlockedAt != null
    val fraction: Float get() = (progress.toFloat() / achievement.target).coerceIn(0f, 1f)
}

/** Everything the farm screen needs, computed from the current sources. */
data class FarmMetrics(
    val harvested: Int = 0,
    val harvestsByCrop: Map<Crop, Int> = emptyMap(),
    val focusMinutes: Int = 0,
    val tasksDone: Int = 0,
    val bestStreak: Int = 0,
    val goalsReached: Int = 0,
) {
    fun of(metric: AchievementMetric): Int = when (metric) {
        AchievementMetric.HARVESTS -> harvested
        AchievementMetric.FOCUS_MINUTES -> focusMinutes
        AchievementMetric.TASKS_DONE -> tasksDone
        AchievementMetric.BEST_STREAK -> bestStreak
        AchievementMetric.GOALS_REACHED -> goalsReached
        AchievementMetric.CROPS_HARVESTED -> harvestsByCrop.count { it.value > 0 }
    }

    fun harvested(crop: Crop): Int = harvestsByCrop[crop] ?: 0
}

/** Everything shown on the farm screen. */
data class FarmSnapshot(
    val plots: List<FarmPlot> = emptyList(),
    val entries: List<NutrientEntry> = emptyList(),
    val harvests: List<HarvestRecord> = emptyList(),
    val achievements: List<AchievementState> = emptyList(),
    val metrics: FarmMetrics = FarmMetrics(),
) {
    val activePlots: List<FarmPlot> get() = plots.filter { !it.isHarvested }.sortedBy { it.slot }
    val plotLimit: Int get() = FarmRules.plotLimit(harvests.size)
    val compost: Int get() = entries.filter { it.plotId == null }.sumOf { it.amount }

    fun growth(plot: FarmPlot): Int = entries.filter { it.plotId == plot.id }.sumOf { it.amount }

    fun unlockedCount(): Int = achievements.count { it.unlocked }
}

/** The farm can be recalculated from the user's data at any time; these are the inputs. */
data class FarmSources(
    val records: List<TimeRecord> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val habits: List<com.meolist.domain.habit.Habit> = emptyList(),
    val checkIns: List<HabitCheckIn> = emptyList(),
    val goals: List<ReachedGoal> = emptyList(),
)

/** The priority bonus for a completed task. */
fun taskPriorityBonus(priority: Priority): Int = when (priority) {
    Priority.HIGH -> FarmRules.TASK_HIGH_BONUS
    Priority.MEDIUM -> FarmRules.TASK_MEDIUM_BONUS
    Priority.LOW, Priority.NONE -> 0
}
