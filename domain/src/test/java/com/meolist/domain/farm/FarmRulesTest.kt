package com.meolist.domain.farm

import com.meolist.domain.common.MINUTE_MS
import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitCheckIn
import com.meolist.domain.task.Priority
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskStatus
import com.meolist.domain.tracker.ReachedGoal
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.TimeRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 24)
    private fun minutes(n: Int) = n * MINUTE_MS

    private fun record(minutes: Int, manual: Boolean = false) = TimeRecord(
        id = 1,
        typeId = 1,
        startAt = 0,
        endAt = minutes(minutes),
        isManual = manual,
    )

    @Test
    fun manualRecordsCountHalfAndEveryRecordIsCapped() {
        assertEquals(90, FarmRules.recordPoints(record(90)))
        assertEquals(45, FarmRules.recordPoints(record(90, manual = true)))
        assertEquals(FarmRules.RECORD_POINT_CAP, FarmRules.recordPoints(record(1_000)))
        assertEquals(0, FarmRules.recordPoints(record(0)))
    }

    @Test
    fun taskPointsRewardPriority() {
        fun task(priority: Priority) = Task(title = "t", priority = priority, status = TaskStatus.DONE)
        assertEquals(10, FarmRules.taskPoints(task(Priority.NONE)))
        assertEquals(10, FarmRules.taskPoints(task(Priority.LOW)))
        assertEquals(15, FarmRules.taskPoints(task(Priority.MEDIUM)))
        assertEquals(20, FarmRules.taskPoints(task(Priority.HIGH)))
    }

    @Test
    fun plotLimitGrowsWithHarvests() {
        assertEquals(3, FarmRules.plotLimit(0))
        assertEquals(3, FarmRules.plotLimit(4))
        assertEquals(4, FarmRules.plotLimit(5))
        assertEquals(4, FarmRules.plotLimit(14))
        assertEquals(5, FarmRules.plotLimit(15))
        assertEquals(5, FarmRules.plotLimit(120))
    }

    @Test
    fun stagesFollowGrowth() {
        val plot = FarmPlot(id = 1, slot = 0, crop = Crop.CARROT, plantedAt = 0)
        fun stage(amount: Int) = FarmRules.stage(plot, listOf(NutrientEntry("r", 1, amount, 0)))

        assertEquals(0, stage(0))
        assertEquals(0, stage(5))
        assertEquals(1, stage(18))
        assertEquals(2, stage(72))
        assertEquals(3, stage(130))
        assertEquals(4, stage(170))
        assertEquals(5, stage(180))
        assertTrue(FarmRules.isMature(plot, listOf(NutrientEntry("r", 1, 180, 0))))
        assertFalse(FarmRules.isMature(plot, listOf(NutrientEntry("r", 1, 179, 0))))
    }

    @Test
    fun cropsUnlockFromHarvestsFocusStreaksAndTheWholeCatalog() {
        val empty = FarmMetrics()
        assertTrue(FarmRules.isCropUnlocked(Crop.CARROT, empty))
        assertFalse(FarmRules.isCropUnlocked(Crop.TOMATO, empty))
        assertFalse(FarmRules.isCropUnlocked(Crop.SUNFLOWER, empty))

        assertTrue(FarmRules.isCropUnlocked(Crop.TOMATO, empty.copy(harvested = 3)))
        assertTrue(FarmRules.isCropUnlocked(Crop.SUNFLOWER, empty.copy(focusMinutes = 600)))
        assertTrue(FarmRules.isCropUnlocked(Crop.PUMPKIN, empty.copy(bestStreak = 21)))

        val almostAll = FarmMetrics(
            harvested = 20,
            harvestsByCrop = Crop.entries.filter { it != Crop.CHERRY }.associateWith { 1 },
        )
        assertTrue(FarmRules.isCropUnlocked(Crop.CHERRY, almostAll))
        assertFalse(FarmRules.isCropUnlocked(Crop.CHERRY, almostAll.copy(harvestsByCrop = mapOf(Crop.CARROT to 3))))
    }

    @Test
    fun achievementStatesClampProgressAndKeepUnlockedOnes() {
        val metrics = FarmMetrics(tasksDone = 7)
        val states = FarmRules.achievementStates(metrics, mapOf(Achievement.TASKS_10.key to 123L))
        val tasks10 = states.first { it.achievement == Achievement.TASKS_10 }
        assertEquals(10, tasks10.progress)
        assertEquals(123L, tasks10.unlockedAt)
        assertTrue(tasks10.unlocked)

        val fresh = FarmRules.achievementStates(metrics, emptyMap())
        val open = fresh.first { it.achievement == Achievement.TASKS_10 }
        assertEquals(7, open.progress)
        assertFalse(open.unlocked)
        assertEquals(0.7f, open.fraction, 0.001f)
    }

    @Test
    fun sourceNutrientsUseStableKeys() {
        val habit = Habit(id = 3, name = "Read", dailyTarget = 3, startDay = today)
        val sources = FarmSources(
            records = listOf(record(30), record(90, manual = true).copy(id = 2)),
            tasks = listOf(
                Task(id = 4, title = "t", priority = Priority.HIGH, status = TaskStatus.DONE, completedAt = 500),
                Task(id = 5, title = "open"),
            ),
            habits = listOf(habit),
            checkIns = listOf(
                HabitCheckIn(3, today, 2),
                HabitCheckIn(3, today.minusDays(1), 3),
            ),
            goals = listOf(ReachedGoal(1, GoalPeriod.DAY, today.toEpochDay(), 900)),
        )

        val nutrients = FarmRules.sourceNutrients(sources, zone)

        assertEquals(30, nutrients["record:1"]!!.amount)
        assertEquals(45, nutrients["record:2"]!!.amount)
        assertEquals(null, nutrients["task:5"])
        assertEquals(20, nutrients["task:4"]!!.amount)
        assertEquals(null, nutrients["habit:3:${today.toEpochDay()}"])
        assertEquals(5, nutrients["habit:3:${today.minusDays(1).toEpochDay()}"]!!.amount)
        assertEquals(today.minusDays(1).startOfDayMillis(zone), nutrients["habit:3:${today.minusDays(1).toEpochDay()}"]!!.earnedAt)
        assertEquals(20, nutrients["goal:1:0:${today.toEpochDay()}"]!!.amount)
    }

    @Test
    fun metricsCountFocusTasksStreaksAndHarvests() {
        val habit = Habit(id = 1, name = "Read", startDay = today.minusDays(10))
        val checkIns = (0..4).map { HabitCheckIn(1, today.minusDays(it.toLong()), 1) }
        val metrics = FarmRules.metrics(
            records = listOf(record(30), record(90)),
            tasks = listOf(
                Task(title = "a", status = TaskStatus.DONE),
                Task(title = "b", status = TaskStatus.DONE, deletedAt = 1),
                Task(title = "c"),
            ),
            habits = listOf(habit),
            checkIns = checkIns,
            goals = listOf(
                ReachedGoal(1, GoalPeriod.DAY, 1, 0),
                ReachedGoal(1, GoalPeriod.WEEK, 1, 0),
            ),
            harvests = listOf(
                HarvestRecord(id = 1, crop = Crop.CARROT, plotId = 1, harvestedAt = 0, nutrients = 200),
                HarvestRecord(id = 2, crop = Crop.CARROT, plotId = 2, harvestedAt = 0, nutrients = 200),
            ),
            today = today,
            firstDayOfWeek = java.time.DayOfWeek.MONDAY,
        )
        assertEquals(120, metrics.focusMinutes)
        assertEquals(1, metrics.tasksDone)
        assertEquals(5, metrics.bestStreak)
        assertEquals(2, metrics.goalsReached)
        assertEquals(2, metrics.harvested)
        assertEquals(2, metrics.harvested(Crop.CARROT))
        assertEquals(1, metrics.of(AchievementMetric.CROPS_HARVESTED))
    }
}
