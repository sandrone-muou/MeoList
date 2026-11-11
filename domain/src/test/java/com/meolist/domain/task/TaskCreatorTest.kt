package com.meolist.domain.task

import com.meolist.domain.common.TransactionRunner
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.reminder.ReminderRepository
import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.testing.FakeAlarmPort
import com.meolist.domain.testing.FakeClock
import com.meolist.domain.testing.FakeNotifier
import com.meolist.domain.testing.FakeReminderRepository
import com.meolist.domain.testing.FakeTaskRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCreatorTest {
    private val repo = FakeTaskRepository()
    private val clock = FakeClock()
    private val reminders = FakeReminderRepository(repo)
    private var inTransaction = false
    private var committed = false
    private val transaction = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T {
            inTransaction = true
            try {
                return block().also { committed = true }
            } finally {
                inTransaction = false
            }
        }
    }

    private fun creator(reminderRepo: ReminderRepository = reminders): TaskCreator {
        val checkedRepo = object : TaskRepository by repo {
            override suspend fun insert(task: Task): Long {
                check(inTransaction)
                return repo.insert(task)
            }
            override suspend fun setTags(taskId: Long, tagIds: Set<Long>) {
                check(inTransaction)
                repo.setTags(taskId, tagIds)
            }
        }
        val scheduler = ReminderScheduler(reminderRepo, checkedRepo, FakeAlarmPort(), FakeNotifier(), clock)
        return TaskCreator(TaskInteractor(checkedRepo, clock, scheduler), transaction, clock, scheduler)
    }

    @Test
    fun selectedCalendarDateDefaultsToAllDayInbox() = runTest {
        val date = LocalDate.of(2026, 11, 15)
        val id = creator().create(TaskDraft("  New task  ", date))
        val saved = repo.get(id)!!
        assertEquals("New task", saved.title)
        assertEquals(date.atStartOfDay(clock.zone()).toInstant().toEpochMilli(), saved.dueAt)
        assertTrue(saved.allDay)
        assertNull(saved.listId)
        assertTrue(repo.tagIdsOf(id).isEmpty())
        assertTrue(committed)
    }

    @Test
    fun editedDateTimeListTagsAndReminderAreSavedTogether() = runTest {
        val date = LocalDate.of(2026, 11, 20)
        val reminderRepo = object : ReminderRepository by reminders {
            override suspend fun replaceForOwner(ownerType: ReminderOwner, ownerId: Long, reminders: List<Reminder>) {
                check(inTransaction)
                this@TaskCreatorTest.reminders.replaceForOwner(ownerType, ownerId, reminders)
            }
        }
        val id = creator(reminderRepo).create(TaskDraft(
            "Timed", date, LocalTime.of(9, 30), Priority.HIGH, 2, setOf(3, 4),
            Reminder(ownerType = ReminderOwner.TASK, ownerId = 999, offsetMin = 15),
        ))
        assertEquals(date.atTime(9, 30).atZone(clock.zone()).toInstant().toEpochMilli(), repo.get(id)!!.dueAt)
        assertFalse(repo.get(id)!!.allDay)
        assertEquals(2L, repo.get(id)!!.listId)
        assertEquals(Priority.HIGH, repo.get(id)!!.priority)
        assertEquals(setOf(3L, 4L), repo.tagIdsOf(id))
        assertEquals(id, reminders.all.single().ownerId)
        assertEquals(repo.get(id)!!.dueAt!! - 15 * 60_000L, reminders.all.single().nextFireAt)
        assertTrue(committed)
    }

    @Test
    fun undatedTaskDoesNotKeepReminder() = runTest {
        creator().create(TaskDraft("No date", reminder = Reminder(ownerType = ReminderOwner.TASK, ownerId = 0)))
        assertNull(repo.all.single().dueAt)
        assertTrue(reminders.all.isEmpty())
    }

    @Test
    fun failedReminderWritePropagatesWithoutCommitting() = runTest {
        val failing = object : ReminderRepository by reminders {
            override suspend fun replaceForOwner(ownerType: ReminderOwner, ownerId: Long, reminders: List<Reminder>) {
                throw IllegalStateException("write failed")
            }
        }
        val result = runCatching {
            creator(failing).create(TaskDraft("Fail", LocalDate.of(2026, 11, 15),
                reminder = Reminder(ownerType = ReminderOwner.TASK, ownerId = 0)))
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertFalse(committed)
        assertFalse(inTransaction)
    }

    @Test
    fun blankTitleDoesNotCreateTask() = runTest {
        assertTrue(runCatching { creator().create(TaskDraft("  ")) }.isFailure)
        assertTrue(repo.all.isEmpty())
        assertFalse(committed)
    }
}
