package com.meolist.domain.farm

import com.meolist.domain.habit.Habit
import com.meolist.domain.task.Priority
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskStatus
import com.meolist.domain.testing.DirectTransactions
import com.meolist.domain.testing.FakeClock
import com.meolist.domain.testing.FakeFarmRepository
import com.meolist.domain.testing.FakeHabitRepository
import com.meolist.domain.testing.FakePreferences
import com.meolist.domain.testing.FakeTaskRepository
import com.meolist.domain.testing.FakeTracker
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.TimeRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmInteractorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = FakeClock(zoneId = zone).apply { set(LocalDateTime.of(2026, 10, 24, 21, 0)) }
    private val farm = FakeFarmRepository()
    private val tracker = FakeTracker()
    private val tasks = FakeTaskRepository()
    private val habits = FakeHabitRepository()
    private val prefs = FakePreferences()
    private val interactor = FarmInteractor(farm, tracker, tasks, habits, tracker, prefs, clock, DirectTransactions)

    private val minute = 60_000L
    private fun at(hour: Int) = LocalDateTime.of(2026, 10, 24, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private suspend fun addRecord(startHour: Int, minutes: Int, manual: Boolean = false): Long {
        val start = at(startHour)
        return tracker.insertRecord(
            TimeRecord(typeId = 1, startAt = start, endAt = start + minutes * minute, isManual = manual),
        )
    }

    private suspend fun addPlot(slot: Int, crop: Crop = Crop.CARROT): Long =
        farm.insertPlot(FarmPlot(slot = slot, crop = crop, plantedAt = 0))

    @Test
    fun newNutrientsGoToTheLowestSlotFirst() = runTest {
        val lowest = addPlot(0)
        val second = addPlot(1)

        addRecord(9, 60)
        interactor.sync()

        val entry = farm.getNutrients().single()
        assertEquals(lowest, entry.plotId)
        assertEquals(60, entry.amount)
        assertEquals(0, FarmRules.growth(farm.getPlots().first { it.id == second }, farm.getNutrients()))
    }

    @Test
    fun editingAndDeletingRecordsRollGrowthBack() = runTest {
        addPlot(0)
        val first = addRecord(9, 100)
        val second = addRecord(10, 50)
        interactor.sync()
        assertEquals(150, totalGrowth())

        tracker.updateRecord(tracker.getRecord(first)!!.copy(endAt = at(9) + 30 * minute))
        interactor.sync()
        assertEquals(80, totalGrowth())

        tracker.deleteRecord(second)
        interactor.sync()
        assertEquals(30, totalGrowth())
        assertEquals(1, farm.getNutrients().size)
    }

    @Test
    fun fullPlotsOverflowIntoCompostAndPlantingCanUseIt() = runTest {
        addPlot(0)
        addPlot(1)
        addPlot(2)
        repeat(9) { addRecord(8 + it, 70) }
        interactor.sync()

        val plots = farm.getPlots()
        plots.forEach { assertEquals(210, FarmRules.growth(it, farm.getNutrients())) }
        assertEquals(0, compost())

        addRecord(20, 50)
        interactor.sync()
        assertEquals(50, compost())
        assertNull(farm.getNutrients().first { it.amount == 50 }.plotId)

        // The ripe crops are harvested, which frees a slot but leaves the compost alone.
        plots.forEach { plot ->
            val result = interactor.harvest(plot.id)
            assertTrue(result is HarvestResult.Harvested)
        }
        assertEquals(3, farm.getHarvests().size)
        assertEquals(50, compost())

        val replanted = interactor.plant(0, Crop.CARROT, useCompost = true)
        assertTrue(replanted is PlantResult.Planted)
        assertEquals(50, (replanted as PlantResult.Planted).compostUsed)
        assertEquals(0, compost())
        val newPlot = farm.getPlots().first { it.id == replanted.plotId }
        assertEquals(50, FarmRules.growth(newPlot, farm.getNutrients()))
    }

    @Test
    fun habitsTasksAndGoalsBecomeCompost() = runTest {
        val today = LocalDate.of(2026, 10, 24)
        val habitId = habits.saveHabit(Habit(name = "Read", dailyTarget = 2, startDay = today))
        habits.setCheckIn(habitId, today, 1)
        tasks.insert(Task(title = "done", priority = Priority.HIGH, status = TaskStatus.DONE, completedAt = 1))
        tracker.markGoalReached(typeId = 1, period = GoalPeriod.DAY, periodStartDay = today.toEpochDay(), at = 2)

        interactor.sync()

        assertEquals(20, farm.getNutrients().single { it.sourceKey.startsWith("task:") }.amount)
        assertEquals(20, farm.getNutrients().single { it.sourceKey.startsWith("goal:") }.amount)
        // The countable habit is not finished yet.
        assertTrue(farm.getNutrients().none { it.sourceKey.startsWith("habit:") })
        assertEquals(40, compost())

        habits.setCheckIn(habitId, today, 2)
        interactor.sync()
        assertEquals(5, farm.getNutrients().single { it.sourceKey.startsWith("habit:") }.amount)
        assertEquals(45, compost())
    }

    @Test
    fun harvestNeedsAFullCropAndUnlocksBadges() = runTest {
        val plotId = addPlot(0)
        addRecord(9, 60)
        interactor.sync()
        assertEquals(HarvestResult.NotReady, interactor.harvest(plotId))

        addRecord(11, 120)
        interactor.sync()
        val harvested = interactor.harvest(plotId)
        assertTrue(harvested is HarvestResult.Harvested)
        val record = (harvested as HarvestResult.Harvested).record
        assertEquals(Crop.CARROT, record.crop)
        assertEquals(180, record.nutrients)
        assertNotNull(farm.getPlots().first { it.id == plotId }.harvestedAt)
        assertTrue(Achievement.FIRST_HARVEST.key in farm.getUnlockedAchievements())

        assertEquals(HarvestResult.Empty, interactor.harvest(plotId))
        // The nutrients stay with the harvested plot as history.
        assertEquals(180, totalGrowth())
    }

    @Test
    fun harvestCapsRewardAtCropRequirement() = runTest {
        val plotId = addPlot(0)
        addRecord(9, 199)
        interactor.sync()
        assertTrue(FarmRules.growth(farm.getPlots().first { it.id == plotId }, farm.getNutrients()) > Crop.CARROT.need)

        val harvested = interactor.harvest(plotId)

        assertTrue(harvested is HarvestResult.Harvested)
        assertEquals(Crop.CARROT.need, (harvested as HarvestResult.Harvested).record.nutrients)
    }

    @Test
    fun plantingChecksSlotLimitOccupancyAndCropUnlocks() = runTest {
        assertTrue(interactor.plant(3, Crop.CARROT) is PlantResult.SlotLocked)
        assertTrue(interactor.plant(0, Crop.TOMATO) is PlantResult.CropLocked)

        val planted = interactor.plant(0, Crop.CARROT)
        assertTrue(planted is PlantResult.Planted)
        assertTrue(interactor.plant(0, Crop.CARROT) is PlantResult.SlotTaken)
        assertEquals(1, farm.getPlots().size)
    }

    @Test
    fun achievementsStayAfterTheirSourceDisappears() = runTest {
        val id = addRecord(9, 60)
        interactor.sync()
        assertTrue(Achievement.FOCUS_1H.key in farm.getUnlockedAchievements())

        tracker.deleteRecord(id)
        interactor.sync()
        assertEquals(0, farm.getNutrients().size)
        assertTrue(Achievement.FOCUS_1H.key in farm.getUnlockedAchievements())
    }

    @Test
    fun syncIsIdempotent() = runTest {
        addPlot(0)
        addRecord(9, 60)
        interactor.sync()
        interactor.sync()
        assertEquals(1, farm.getNutrients().size)
        assertEquals(60, totalGrowth())
    }

    private suspend fun totalGrowth(): Int =
        farm.getPlots().sumOf { FarmRules.growth(it, farm.getNutrients()) }

    private suspend fun compost(): Int = farm.getNutrients().filter { it.plotId == null }.sumOf { it.amount }
}
