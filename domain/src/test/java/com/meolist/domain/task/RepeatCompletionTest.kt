package com.meolist.domain.task

import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.toMillis
import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.testing.FakeAlarmPort
import com.meolist.domain.testing.FakeClock
import com.meolist.domain.testing.FakeNotifier
import com.meolist.domain.testing.FakeReminderRepository
import com.meolist.domain.testing.FakeTaskRepository
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

class RepeatCompletionTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val repo = FakeTaskRepository()
    private val clock = FakeClock(zoneId = zone).apply { set(LocalDateTime.of(2026, 10, 24, 10, 0)) }
    private val scheduler = ReminderScheduler(FakeReminderRepository(repo), repo, FakeAlarmPort(), FakeNotifier(), clock)
    private val interactor = TaskInteractor(repo, clock, scheduler)

    private fun at(dateTime: String, z: ZoneId = zone) = LocalDateTime.parse(dateTime).toMillis(z)
    private fun dateOf(millis: Long?, z: ZoneId = zone) = millis!!.toLocalDateTime(z).toLocalDate()

    private suspend fun repeating(rule: String, due: Long?, allDay: Boolean = true, fromCompletion: Boolean = false): Long {
        val id = interactor.add("Water plants", dueAt = due, allDay = allDay, tagIds = setOf(7))
        interactor.save(repo.get(id)!!.copy(repeatRule = rule, repeatFromCompletion = fromCompletion))
        return id
    }

    @Test
    fun completing_advancesTask_andKeepsDoneCopy() = runTest {
        val id = repeating("FREQ=DAILY", at("2026-10-24T00:00"))
        val result = interactor.setDone(id, true)

        assertNotNull(result)
        val task = repo.get(id)!!
        assertFalse(task.isDone)
        assertEquals(LocalDate.of(2026, 10, 25), dateOf(task.dueAt))
        val copy = repo.get(result!!.copyId)!!
        assertTrue(copy.isDone)
        assertEquals(id, copy.repeatOriginId)
        assertNull(copy.repeatRule)
        assertEquals(LocalDate.of(2026, 10, 24), dateOf(copy.dueAt))
        assertEquals(setOf(7L), repo.tagIdsOf(copy.id))
    }

    @Test
    fun overdue_skipsToAfterToday() = runTest {
        val id = repeating("FREQ=DAILY", at("2026-10-20T00:00"))
        interactor.setDone(id, true)
        assertEquals(LocalDate.of(2026, 10, 25), dateOf(repo.get(id)!!.dueAt))
    }

    @Test
    fun completingEarly_goesAfterDueDate() = runTest {
        val id = repeating("FREQ=WEEKLY", at("2026-10-28T00:00"))
        interactor.setDone(id, true)
        assertEquals(LocalDate.of(2026, 11, 4), dateOf(repo.get(id)!!.dueAt))
    }

    @Test
    fun keepsTimeOfDay_acrossDstChange() = runTest {
        val ny = ZoneId.of("America/New_York")
        val nyClock = FakeClock(zoneId = ny).apply { set(LocalDateTime.of(2026, 10, 31, 12, 0)) }
        val nyScheduler = ReminderScheduler(FakeReminderRepository(repo), repo, FakeAlarmPort(), FakeNotifier(), nyClock)
        val nyInteractor = TaskInteractor(repo, nyClock, nyScheduler)
        // DST ends on 2026-11-01 in New York.
        val id = nyInteractor.add("Stand-up", dueAt = at("2026-10-31T09:30", ny), allDay = false)
        nyInteractor.save(repo.get(id)!!.copy(repeatRule = "FREQ=DAILY", startAt = at("2026-10-31T09:00", ny)))
        nyInteractor.setDone(id, true)

        val task = repo.get(id)!!
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 30), task.dueAt!!.toLocalDateTime(ny))
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), task.startAt!!.toLocalDateTime(ny))
        assertFalse(task.allDay)
    }

    @Test
    fun count_decrements_andLastOccurrenceCompletes() = runTest {
        val id = repeating("FREQ=DAILY;COUNT=2", at("2026-10-24T00:00"))
        assertNotNull(interactor.setDone(id, true))
        assertEquals("FREQ=DAILY;COUNT=1", repo.get(id)!!.repeatRule)

        assertNull(interactor.setDone(id, true))
        assertTrue(repo.get(id)!!.isDone)
    }

    @Test
    fun until_endsSeries() = runTest {
        val id = repeating("FREQ=DAILY;UNTIL=20261024", at("2026-10-24T00:00"))
        assertNull(interactor.setDone(id, true))
        assertTrue(repo.get(id)!!.isDone)
    }

    @Test
    fun fromCompletion_countsFromToday() = runTest {
        val id = repeating("FREQ=DAILY;INTERVAL=3", at("2026-10-10T00:00"), fromCompletion = true)
        interactor.setDone(id, true)
        assertEquals(LocalDate.of(2026, 10, 27), dateOf(repo.get(id)!!.dueAt))
    }

    @Test
    fun noDueDate_startsFromToday() = runTest {
        val id = repeating("FREQ=DAILY", due = null)
        interactor.setDone(id, true)
        val task = repo.get(id)!!
        assertEquals(LocalDate.of(2026, 10, 25), dateOf(task.dueAt))
        assertTrue(task.allDay)
    }

    @Test
    fun subtasksReset_andUndoRestoresEverything() = runTest {
        val id = repeating("FREQ=DAILY", at("2026-10-24T00:00"))
        val sub = interactor.add("Fill can", parentId = id)
        interactor.setDone(sub, true)
        val before = repo.get(id)!!

        val advance = interactor.setDone(id, true)!!
        assertFalse(repo.get(sub)!!.isDone)

        interactor.undoRepeat(advance)
        assertNull(repo.get(advance.copyId))
        assertEquals(before.dueAt, repo.get(id)!!.dueAt)
        assertEquals(before.repeatRule, repo.get(id)!!.repeatRule)
        assertTrue(repo.get(sub)!!.isDone)
    }

    @Test
    fun save_normalizesAndDropsInvalidRules() = runTest {
        val id = interactor.add("x")
        interactor.save(repo.get(id)!!.copy(repeatRule = "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO", repeatFromCompletion = true))
        assertEquals("FREQ=WEEKLY;BYDAY=MO", repo.get(id)!!.repeatRule)
        interactor.save(repo.get(id)!!.copy(repeatRule = "bogus"))
        assertNull(repo.get(id)!!.repeatRule)
        assertFalse(repo.get(id)!!.repeatFromCompletion)
    }
}
