package com.meolist.domain.tracker

import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.settings.AppSettings
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.task.TaskList
import com.meolist.domain.testing.DirectTransactions
import com.meolist.domain.testing.FakeAlarmPort
import com.meolist.domain.testing.FakeClock
import com.meolist.domain.testing.FakeGoalAlarmPort
import com.meolist.domain.testing.FakeNotifier
import com.meolist.domain.testing.FakePreferences
import com.meolist.domain.testing.FakeReminderRepository
import com.meolist.domain.testing.FakeTaskListRepository
import com.meolist.domain.testing.FakeTaskRepository
import com.meolist.domain.testing.FakeTimerNotifier
import com.meolist.domain.testing.FakeTracker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimerInteractorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = FakeClock(zoneId = zone).apply { set(LocalDateTime.of(2026, 10, 20, 10, 0)) }
    private val tracker = FakeTracker()
    private val tasks = FakeTaskRepository()
    private val lists = FakeTaskListRepository()
    private val prefs = FakePreferences()
    private val notifier = FakeTimerNotifier()
    private val goalAlarm = FakeGoalAlarmPort()
    private val sync = TimerSync(tracker, tracker, tasks, prefs, notifier, goalAlarm, clock)
    private val timers = TimerInteractor(tracker, tracker, tasks, lists, prefs, DirectTransactions, sync, clock)

    private val minute = 60_000L

    private suspend fun type(name: String, archived: Boolean = false): Long =
        tracker.insertType(ActivityType(name = name, icon = "timer", color = 0, archived = archived))

    private fun at(hour: Int, minuteOfHour: Int, day: Int = 20): Long =
        LocalDateTime.of(2026, 10, day, hour, minuteOfHour).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun stop_turnsTimerIntoRecord() = runTest {
        val work = type("Work")
        val id = timers.start(work)
        assertEquals(1, notifier.shown.size)
        clock.advance(25 * minute)

        val recordId = timers.stop(id)!!

        val record = tracker.getRecord(recordId)!!
        assertEquals(at(10, 0), record.startAt)
        assertEquals(25 * minute, record.durationMs)
        assertTrue(tracker.getRunning().isEmpty())
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun stop_discardsRecordsShorterThanThreshold() = runTest {
        val id = timers.start(type("Work"))
        clock.advance(59_000)
        assertNull(timers.stop(id))
        assertTrue(tracker.records.value.isEmpty())
        assertTrue(tracker.getRunning().isEmpty())
    }

    @Test
    fun stop_afterClockMovedBack_keepsNothing() = runTest {
        prefs.updateSettings { it.copy(ignoreShortRecordSec = 0) }
        val id = timers.start(type("Work"))
        clock.advance(-10 * minute)
        assertNull(timers.stop(id))
        assertTrue(tracker.records.value.isEmpty())
    }

    @Test
    fun parallelTimers_runSideBySide_andSurviveAsStoredStarts() = runTest {
        val a = type("A")
        val b = type("B")
        val c = type("C")
        timers.start(a)
        clock.advance(minute)
        timers.start(b)
        clock.advance(minute)
        timers.start(c)

        assertEquals(3, tracker.getRunning().size)
        assertEquals(listOf("A", "B", "C"), notifier.shown.map { it.type.name })
        // Elapsed time is derived from the stored start only, so a restart loses nothing.
        clock.advance(10 * minute)
        val elapsed = tracker.getRunning().associate { it.typeId to clock.now() - it.startedAt }
        assertEquals(mapOf(a to 12 * minute, b to 11 * minute, c to 10 * minute), elapsed)
    }

    @Test
    fun singleTimerMode_stopsOthersOnStart() = runTest {
        prefs.updateSettings { it.copy(multipleTimers = false) }
        val a = type("A")
        val b = type("B")
        timers.start(a)
        clock.advance(5 * minute)
        timers.start(b)

        assertEquals(listOf(b), tracker.getRunning().map { it.typeId })
        assertEquals(listOf(a), tracker.records.value.values.map { it.typeId })
    }

    @Test
    fun startingSameTypeForSameTask_keepsRunningTimer() = runTest {
        val a = type("A")
        val first = timers.start(a, taskId = 7)
        clock.advance(5 * minute)
        val second = timers.start(a, taskId = 7)

        assertEquals(first, second)
        assertEquals(at(10, 0), tracker.getRunning(first)!!.startedAt)
        assertTrue(tracker.records.value.isEmpty())
    }

    @Test
    fun startingSameTypeForOtherTask_recordsPreviousFirst() = runTest {
        val a = type("A")
        timers.start(a, taskId = 1)
        clock.advance(5 * minute)
        timers.start(a, taskId = 2)

        assertEquals(listOf(1L), tracker.records.value.values.map { it.taskId })
        assertEquals(listOf(2L), tracker.getRunning().map { it.taskId })
    }

    @Test
    fun completingTask_stopsOnlyItsTimers() = runTest {
        val scheduler = ReminderScheduler(FakeReminderRepository(tasks), tasks, FakeAlarmPort(), FakeNotifier(), clock)
        val taskInteractor = TaskInteractor(tasks, clock, scheduler, StopTimersOnCompletion(timers, prefs))
        val task = taskInteractor.add("Write report")
        val other = taskInteractor.add("Read")
        timers.start(type("Work"), taskId = task)
        timers.start(type("Study"), taskId = other)
        clock.advance(3 * minute)

        taskInteractor.setDone(task, true)

        assertEquals(listOf(other), tracker.getRunning().map { it.taskId })
        assertEquals(listOf(task), tracker.records.value.values.map { it.taskId })
    }

    @Test
    fun completingTask_keepsTimersWhenTurnedOff() = runTest {
        prefs.updateSettings { it.copy(stopTimerOnTaskComplete = false) }
        val scheduler = ReminderScheduler(FakeReminderRepository(tasks), tasks, FakeAlarmPort(), FakeNotifier(), clock)
        val taskInteractor = TaskInteractor(tasks, clock, scheduler, StopTimersOnCompletion(timers, prefs))
        val task = taskInteractor.add("Write report")
        timers.start(type("Work"), taskId = task)

        taskInteractor.setDone(task, true)

        assertEquals(1, tracker.getRunning().size)
    }

    @Test
    fun goalAlarm_firesWhenRemainingTimeRunsOut() = runTest {
        val work = timers.saveType(ActivityType(name = "Work", icon = "timer", color = 0), listOf(ActivityGoal(0, GoalPeriod.DAY, 3600)))
        tracker.insertRecord(TimeRecord(typeId = work, startAt = at(8, 0), endAt = at(8, 20)))
        assertNull(goalAlarm.scheduledAt)

        timers.start(work)

        assertEquals(at(10, 40), goalAlarm.scheduledAt)
    }

    @Test
    fun goalAlarm_isCappedAtPeriodEnd() = runTest {
        clock.set(LocalDateTime.of(2026, 10, 20, 23, 30))
        val work = timers.saveType(ActivityType(name = "Work", icon = "timer", color = 0), listOf(ActivityGoal(0, GoalPeriod.DAY, 7200)))
        timers.start(work)
        assertEquals(at(0, 0, day = 21), goalAlarm.scheduledAt)
    }

    @Test
    fun goalReached_isAnnouncedOncePerPeriod() = runTest {
        val work = timers.saveType(ActivityType(name = "Work", icon = "timer", color = 0), listOf(ActivityGoal(0, GoalPeriod.DAY, 1800)))
        timers.start(work)
        clock.advance(30 * minute)

        sync.sync()
        sync.sync()

        assertEquals(1, notifier.goalsReached.size)
        assertNull(goalAlarm.scheduledAt)

        // The next day starts from zero again.
        clock.advance(24 * 60 * minute)
        timers.stopAll()
        timers.start(work)
        clock.advance(30 * minute)
        sync.sync()
        assertEquals(2, notifier.goalsReached.size)
    }

    @Test
    fun saveType_keepsOneGoalPerPeriod_andDropsEmptyOnes() = runTest {
        val id = timers.saveType(
            ActivityType(name = "  Work ", icon = "timer", color = 0),
            listOf(
                ActivityGoal(0, GoalPeriod.DAY, 3600),
                ActivityGoal(0, GoalPeriod.DAY, 7200),
                ActivityGoal(0, GoalPeriod.WEEK, 0),
            ),
        )
        assertEquals("Work", tracker.getType(id)!!.name)
        assertEquals(listOf(ActivityGoal(id, GoalPeriod.DAY, 3600)), tracker.getGoals())
    }

    @Test
    fun archivingType_stopsItsTimer() = runTest {
        val work = type("Work")
        timers.start(work)
        clock.advance(5 * minute)
        timers.setArchived(work, true)

        assertTrue(tracker.getRunning().isEmpty())
        assertEquals(1, tracker.records.value.size)
        assertTrue(tracker.getType(work)!!.archived)
    }

    @Test
    fun defaultTypeFor_prefersListDefault_thenLastUsed_skippingArchived() = runTest {
        val listType = type("List")
        val lastUsed = type("Last")
        val listId = lists.saveList(TaskList(name = "Job", color = 0, defaultActivityTypeId = listType))
        val task = tasks.insert(com.meolist.domain.task.Task(title = "t", listId = listId))
        assertEquals(listType, timers.defaultTypeFor(task))

        tracker.insertRecord(TimeRecord(typeId = lastUsed, taskId = task, startAt = at(8, 0), endAt = at(9, 0)))
        timers.setArchived(listType, true)
        assertEquals(lastUsed, timers.defaultTypeFor(task))

        timers.setArchived(lastUsed, true)
        assertNull(timers.defaultTypeFor(task))
    }

    @Test
    fun setListDefaultType_updatesList() = runTest {
        val work = type("Work")
        val listId = lists.saveList(TaskList(name = "Job", color = 0))
        timers.setListDefaultType(listId, work)
        assertEquals(work, lists.getList(listId)!!.defaultActivityTypeId)
    }

    @Test
    fun deleteAndRestoreRecord_roundTrips() = runTest {
        val work = type("Work")
        val id = timers.addRecord(TimeRecord(typeId = work, startAt = at(8, 0), endAt = at(9, 0), comment = " x "))
        val record = tracker.getRecord(id)!!
        assertTrue(record.isManual)
        assertEquals("x", record.comment)

        val deleted = timers.deleteRecord(id)!!
        assertTrue(tracker.records.value.isEmpty())
        timers.restoreRecord(deleted)
        assertEquals(listOf(60 * minute), tracker.records.value.values.map { it.durationMs })
    }

    @Test
    fun defaults_matchDocumentedSettings() {
        val s = AppSettings()
        assertTrue(s.multipleTimers)
        assertEquals(60, s.ignoreShortRecordSec)
    }
}
