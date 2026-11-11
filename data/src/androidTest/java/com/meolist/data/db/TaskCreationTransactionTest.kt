package com.meolist.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meolist.data.repo.ReminderRepositoryImpl
import com.meolist.data.repo.RoomTransactionRunner
import com.meolist.data.repo.TaskRepositoryImpl
import com.meolist.domain.common.Clock
import com.meolist.domain.reminder.AlarmPort
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderNotifier
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.reminder.ReminderRepository
import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskCreator
import com.meolist.domain.task.TaskDraft
import com.meolist.domain.task.TaskInteractor
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskCreationTransactionTest {
    private lateinit var db: MeoDatabase
    private val clock = object : Clock {
        override fun now() = 0L
        override fun zone(): ZoneId = ZoneId.of("Asia/Shanghai")
    }
    private val alarm = object : AlarmPort {
        override fun schedule(atMillis: Long?) = Unit
        override fun canScheduleExact() = true
    }
    private val notifier = object : ReminderNotifier {
        override fun showTaskReminder(task: Task, reminderId: Long) = Unit
        override fun cancelTaskReminder(taskId: Long) = Unit
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MeoDatabase::class.java).build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun reminderFailureRollsBackTaskAndRetryCreatesOnlyOneTask() = runBlocking {
        val tasks = TaskRepositoryImpl(db.taskDao())
        val storedReminders = ReminderRepositoryImpl(db.reminderDao())
        var fail = true
        val reminders = object : ReminderRepository by storedReminders {
            override suspend fun replaceForOwner(ownerType: ReminderOwner, ownerId: Long, reminders: List<Reminder>) {
                storedReminders.replaceForOwner(ownerType, ownerId, reminders)
                if (fail) error("Simulated failure after reminder insertion")
            }
        }
        val scheduler = ReminderScheduler(reminders, tasks, alarm, notifier, clock)
        val creator = TaskCreator(TaskInteractor(tasks, clock, scheduler), RoomTransactionRunner(db), clock, scheduler)
        val date = LocalDate.of(2026, 11, 15)
        val draft = TaskDraft("Calendar task", date, LocalTime.of(9, 0),
            reminder = Reminder(ownerType = ReminderOwner.TASK, ownerId = 0, offsetMin = 10))

        assertTrue(runCatching { creator.create(draft) }.isFailure)
        assertTrue(tasks.getAll().isEmpty())
        assertTrue(storedReminders.getAll().isEmpty())

        fail = false
        val id = creator.create(draft)
        assertEquals(id, tasks.getAll().single().id)
        assertEquals(id, storedReminders.getAll().single().ownerId)
        assertEquals(date.atTime(8, 50).atZone(clock.zone()).toInstant().toEpochMilli(), storedReminders.getAll().single().nextFireAt)
    }
}
