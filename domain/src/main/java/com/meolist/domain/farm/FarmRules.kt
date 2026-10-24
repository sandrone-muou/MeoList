package com.meolist.domain.farm

import com.meolist.domain.common.MINUTE_MS
import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitCheckIn
import com.meolist.domain.habit.HabitRules
import com.meolist.domain.task.Task
import com.meolist.domain.tracker.ReachedGoal
import com.meolist.domain.tracker.TimeRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure farm arithmetic: how much a source is worth, how far a plot has grown, which crops are
 * available and how badges progress. The ledger keeps track of what is already assigned, this object
 * only decides values.
 */
object FarmRules {
    /** A single focus stretch is worth at most four hours, so an accidental 20h timer does not skip a crop. */
    const val RECORD_POINT_CAP = 240
    const val TASK_POINTS = 10
    const val TASK_HIGH_BONUS = 10
    const val TASK_MEDIUM_BONUS = 5
    const val HABIT_POINTS = 5
    const val GOAL_POINTS = 20

    const val BASE_PLOTS = 3
    const val EXTRA_PLOT_AT = 5
    const val SECOND_EXTRA_PLOT_AT = 15
    const val MAX_PLOTS = BASE_PLOTS + 2

    /** Manual entries count half: they were not actually timed. */
    fun recordPoints(record: TimeRecord): Int {
        if (record.durationMs <= 0) return 0
        val minutes = (record.durationMs / MINUTE_MS).toInt()
        if (minutes <= 0) return 0
        val counted = if (record.isManual) minutes / 2 else minutes
        return counted.coerceIn(1, RECORD_POINT_CAP)
    }

    fun taskPoints(task: Task): Int = TASK_POINTS + taskPriorityBonus(task.priority)

    fun plotLimit(harvestCount: Int): Int = when {
        harvestCount >= SECOND_EXTRA_PLOT_AT -> MAX_PLOTS
        harvestCount >= EXTRA_PLOT_AT -> BASE_PLOTS + 1
        else -> BASE_PLOTS
    }

    fun growth(plot: FarmPlot, entries: List<NutrientEntry>): Int =
        entries.filter { it.plotId == plot.id }.sumOf { it.amount }

    fun progress(plot: FarmPlot, entries: List<NutrientEntry>): Float =
        (growth(plot, entries).toFloat() / plot.crop.need).coerceIn(0f, 1f)

    fun isMature(plot: FarmPlot, entries: List<NutrientEntry>): Boolean =
        growth(plot, entries) >= plot.crop.need

    /**
     * 0 seed, 1 sprout, 2 growing, 3 budding, 4 almost ripe, 5 ripe. The art in the UI draws these
     * six looks.
     */
    fun stage(plot: FarmPlot, entries: List<NutrientEntry>): Int {
        val fraction = growth(plot, entries).toFloat() / plot.crop.need
        return when {
            fraction < 0.05f -> 0
            fraction < 0.20f -> 1
            fraction < 0.50f -> 2
            fraction < 0.80f -> 3
            fraction < 1f -> 4
            else -> 5
        }
    }

    fun isCropUnlocked(crop: Crop, metrics: FarmMetrics): Boolean = when (val unlock = crop.unlock) {
        CropUnlock.Always -> true
        is CropUnlock.Harvests -> metrics.harvested >= unlock.count
        is CropUnlock.FocusMinutes -> metrics.focusMinutes >= unlock.minutes
        is CropUnlock.StreakDays -> metrics.bestStreak >= unlock.days
        CropUnlock.AllOtherCrops -> Crop.entries
            .filter { it != crop }
            .all { metrics.harvested(it) > 0 }
    }

    fun achievementProgress(achievement: Achievement, metrics: FarmMetrics): Int =
        metrics.of(achievement.metric).coerceAtMost(achievement.target)

    fun achievementStates(metrics: FarmMetrics, unlockedAt: Map<String, Long>): List<AchievementState> =
        Achievement.entries.map { achievement ->
            val at = unlockedAt[achievement.key]
            AchievementState(
                achievement = achievement,
                progress = if (at != null) achievement.target else achievementProgress(achievement, metrics),
                unlockedAt = at,
            )
        }

    fun metrics(
        records: List<TimeRecord>,
        tasks: List<Task>,
        habits: List<Habit>,
        checkIns: List<HabitCheckIn>,
        goals: List<ReachedGoal>,
        harvests: List<HarvestRecord>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): FarmMetrics = FarmMetrics(
        harvested = harvests.size,
        harvestsByCrop = harvests.groupingBy { it.crop }.eachCount(),
        focusMinutes = records.sumOf { (it.durationMs / MINUTE_MS).toInt() },
        tasksDone = tasks.count { it.isDone && !it.isDeleted },
        bestStreak = habits.maxOfOrNull { HabitRules.bestStreak(it, checkIns, today, firstDayOfWeek) } ?: 0,
        goalsReached = goals.size,
    )

    /**
     * Turns every source into the nutrients it is worth. Keys are stable so the ledger can be
     * reconciled: a source that disappears takes its nutrients with it.
     */
    fun sourceNutrients(
        sources: FarmSources,
        zone: ZoneId,
    ): Map<String, NutrientAmount> {
        val result = linkedMapOf<String, NutrientAmount>()
        sources.records.forEach { record ->
            val points = recordPoints(record)
            if (points > 0) result["record:${record.id}"] = NutrientAmount(points, record.endAt)
        }
        val habitsById = sources.habits.associateBy { it.id }
        sources.checkIns.forEach { checkIn ->
            val habit = habitsById[checkIn.habitId] ?: return@forEach
            if (!HabitRules.isDone(habit, checkIn.value)) return@forEach
            result["habit:${checkIn.habitId}:${checkIn.day.toEpochDay()}"] =
                NutrientAmount(HABIT_POINTS, checkIn.day.startOfDayMillis(zone))
        }
        sources.tasks.forEach { task ->
            if (!task.isDone || task.isDeleted) return@forEach
            result["task:${task.id}"] = NutrientAmount(
                taskPoints(task),
                task.completedAt ?: task.updatedAt,
            )
        }
        sources.goals.forEach { goal ->
            result["goal:${goal.typeId}:${goal.period.value}:${goal.periodStartDay}"] =
                NutrientAmount(GOAL_POINTS, goal.reachedAt)
        }
        return result
    }
}

/** Value of one source and when it was earned. */
data class NutrientAmount(val amount: Int, val earnedAt: Long)
