package com.meolist.domain.tracker

import com.meolist.domain.farm.FarmRules
import com.meolist.domain.settings.AppSettings
import com.meolist.domain.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TimerStagesTest {
    private val clock = FakeClock(nowMs = 1_000_000)
    private val store = FakeTracker()
    private val tasks = FakeTaskRepository()
    private val lists = FakeTaskListRepository()
    private val prefs = FakePreferences(AppSettings(ignoreShortRecordSec = 0))
    private val sync = TimerSync(store, store, tasks, prefs, FakeTimerNotifier(), FakeGoalAlarmPort(), clock)
    private fun interactor() = TimerInteractor(store, store, tasks, lists, prefs, DirectTransactions, sync, clock)
    private val timers = interactor()

    private suspend fun activity(name: String = "Study") = store.insertType(ActivityType(name = name, icon = "timer", color = 1))
    private suspend fun project(type: Long, name: String) = timers.saveSubproject(ActivitySubproject(typeId = type, name = name, color = 2))

    @Test fun marksKeepOverallStartAndOnlyCloseCompletedStages() = runTest {
        val type = activity()
        val reading = project(type, "Reading")
        val practice = project(type, "Practice")
        val id = timers.start(type, stagePlan = listOf(reading, practice))
        val start = clock.now()
        clock.advance(600_000)
        assertTrue(timers.mark(id))
        val running = store.getRunning(id)!!
        assertEquals(start, running.startedAt)
        assertEquals(listOf("Reading"), running.stages.filter { it.endAt != null }.map { it.name })
        assertEquals(600_000L, running.stages.first().durationMs)
        assertEquals("Practice", TimerStages.current(running).name)
        assertEquals(0L, TimerStages.current(running).durationMs)
        assertTrue(store.getAllRecords().isEmpty())
        clock.advance(300_000)
        val saved = store.getRecord(timers.stop(id)!!)!!
        assertEquals(900_000L, saved.durationMs)
        assertEquals(saved.durationMs, saved.stages.sumOf { it.durationMs })
        assertEquals(listOf(600_000L, 300_000L), saved.stages.map { it.durationMs })
    }

    @Test fun exhaustedPlanIsUnassignedAndCanBeAssigned() = runTest {
        val type = activity()
        val reading = project(type, "Reading")
        val id = timers.start(type, stagePlan = listOf(reading))
        clock.advance(1000)
        timers.mark(id)
        assertNull(TimerStages.current(store.getRunning(id)!!).subprojectId)
        timers.assignRunningStage(id, 2, reading)
        clock.advance(1000)
        val record = store.getRecord(timers.stop(id)!!)!!
        assertEquals(listOf(reading, reading), record.stages.map { it.subprojectId })
        timers.assignRecordStage(record.id, 1, null)
        assertNull(store.getRecord(record.id)!!.stages.first().subprojectId)
    }

    @Test fun planAndBoundariesSurviveNewInteractorAndDoNotRepeatNextSession() = runTest {
        val type = activity()
        val id = timers.start(type, stagePlan = listOf(project(type, "Read"), null))
        clock.advance(1000)
        timers.mark(id)
        val restarted = interactor()
        clock.advance(2000)
        val record = store.getRecord(restarted.stop(id)!!)!!
        assertEquals(listOf(1000L, 2000L), record.stages.map { it.durationMs })
        val next = restarted.start(type)
        assertTrue(store.getRunning(next)!!.stages.isEmpty())
    }

    @Test fun zeroLengthMarksAndClockRollbackNeverCreateNegativeStages() = runTest {
        val id = timers.start(activity())
        assertFalse(timers.mark(id))
        clock.advance(1000)
        assertTrue(timers.mark(id))
        assertFalse(timers.mark(id))
        clock.advance(-2000)
        assertFalse(timers.mark(id))
        val record = store.getRecord(timers.stop(id)!!)!!
        assertEquals(1000L, record.durationMs)
        assertEquals(listOf(1000L), record.stages.map { it.durationMs })
    }

    @Test fun thresholdAppliesToWholeSessionAndFarmStillSeesOneRecord() = runTest {
        prefs.updateSettings { it.copy(ignoreShortRecordSec = 60) }
        val id = timers.start(activity())
        repeat(100) { clock.advance(30_000); timers.mark(id) }
        val record = store.getRecord(timers.stop(id)!!)!!
        assertEquals(100, record.stages.size)
        assertEquals(1, store.getAllRecords().size)
        assertEquals(50, FarmRules.recordPoints(record))
        assertEquals(record.durationMs, record.stages.sumOf { it.durationMs })
    }

    @Test fun tooShortSessionAndDiscardKeepNoRecord() = runTest {
        prefs.updateSettings { it.copy(ignoreShortRecordSec = 60) }
        val type = activity()
        val id = timers.start(type)
        clock.advance(10_000); timers.mark(id)
        assertNull(timers.stop(id))
        val second = timers.start(type)
        clock.advance(1000); timers.mark(second)
        timers.discard(second)
        assertTrue(store.getAllRecords().isEmpty())
        assertTrue(store.getRunning().isEmpty())
    }

    @Test fun stopAllAndTaskCompletionCloseTheirFinalStages() = runTest {
        val first = timers.start(activity("A"), taskId = 7, stagePlan = listOf(null))
        val second = timers.start(activity("B"), stagePlan = listOf(null))
        clock.advance(1000); timers.mark(first); timers.mark(second)
        clock.advance(1000); timers.stopForTask(7)
        assertEquals(listOf(second), store.getRunning().map { it.id })
        clock.advance(1000); timers.stopAll()
        assertEquals(listOf(2000L, 3000L), store.getAllRecords().map { it.durationMs })
        store.getAllRecords().forEach { assertEquals(it.durationMs, it.stages.sumOf { s -> s.durationMs }) }
    }

    @Test fun archivingOrSingleTimerReplacementKeepsStages() = runTest {
        prefs.updateSettings { it.copy(multipleTimers = false) }
        val a = activity("A")
        timers.start(a, stagePlan = listOf(null))
        clock.advance(1000)
        val b = activity("B")
        val bid = timers.start(b)
        clock.advance(1000); timers.mark(bid)
        clock.advance(1000); timers.setArchived(b, true)
        assertTrue(store.getRunning().isEmpty())
        store.getAllRecords().forEach { assertEquals(it.durationMs, it.stages.sumOf { s -> s.durationMs }) }
    }

    @Test fun renameArchiveAndDeleteUndoPreserveSnapshots() = runTest {
        val type = activity()
        val pid = project(type, "Original")
        val id = timers.start(type, stagePlan = listOf(pid))
        timers.saveSubproject(store.getSubproject(pid)!!.copy(name = "Renamed", archived = true))
        clock.advance(1000)
        val record = store.getRecord(timers.stop(id)!!)!!
        assertEquals("Original", record.stages.first().name)
        val deleted = timers.deleteRecord(record.id)!!
        timers.restoreRecord(deleted)
        val restored = store.getAllRecords().single()
        assertNotEquals(record.id, restored.id)
        assertEquals(record.stages, restored.stages)
    }

    @Test fun editsClipAndFillStagesAndTypeChangesClearOnlyAssignments() = runTest {
        val type = activity()
        val id = timers.start(type, stagePlan = listOf(project(type, "Read")))
        clock.advance(1000); timers.mark(id)
        clock.advance(1000)
        val record = store.getRecord(timers.stop(id)!!)!!
        val expanded = record.copy(startAt = record.startAt - 1000)
        timers.saveRecord(expanded)
        var edited = store.getRecord(record.id)!!
        assertEquals(3000L, edited.stages.sumOf { it.durationMs })
        assertNull(edited.stages.first().subprojectId)
        timers.saveRecord(edited.copy(startAt = record.startAt + 500, typeId = activity("Other")))
        edited = store.getRecord(record.id)!!
        assertEquals(1500L, edited.stages.sumOf { it.durationMs })
        assertTrue(edited.stages.all { it.subprojectId == null })
    }

    @Test fun concurrentMarkAndStopLeaveOneConsistentRecord() = runTest {
        val id = timers.start(activity(), stagePlan = listOf(null))
        clock.advance(1000)
        val mark = async { timers.mark(id) }
        val stop = async { timers.stop(id) }
        mark.await(); stop.await()
        val record = store.getAllRecords().single()
        assertEquals(record.durationMs, record.stages.sumOf { it.durationMs })
    }

    @Test fun subprojectsFromOtherActivityAreRejected() = runTest {
        val a = activity("A")
        val b = activity("B")
        val project = project(b, "Foreign")
        assertTrue(runCatching { timers.start(a, stagePlan = listOf(project)) }.isFailure)
        assertTrue(store.getRunning().isEmpty())
    }
}
