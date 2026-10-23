package com.meolist.domain.reminder

import com.meolist.domain.common.MINUTE_MS
import com.meolist.domain.common.toMillis
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.testing.FakeAlarmPort
import com.meolist.domain.testing.FakeClock
import com.meolist.domain.testing.FakeNotifier
import com.meolist.domain.testing.FakeReminderRepository
import com.meolist.domain.testing.FakeTaskRepository
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulerTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val tasks = FakeTaskRepository()
    private val reminders = FakeReminderRepository(tasks)
    private val alarm = FakeAlarmPort()
    private val notifier = FakeNotifier()
    private val clock = FakeClock(zoneId = zone).apply { set(LocalDateTime.of(2026, 10, 24, 8, 0)) }
    private val scheduler = ReminderScheduler(reminders, tasks, alarm, notifier, clock)
    private val interactor = TaskInteractor(tasks, clock, scheduler)

    private fun at(text: String) = LocalDateTime.parse(text).toMillis(zone)
    private fun relative(offsetMin: Int, minuteOfDay: Int? = null) =
        Reminder(ownerType = ReminderOwner.TASK, ownerId = 0, offsetMin = offsetMin, minuteOfDay = minuteOfDay)

    @Test
    fun timedTask_firesOffsetBeforeDue() = runTest {
        val id = interactor.add("Call", dueAt = at("2026-10-24T15:00"), allDay = false)
        interactor.setReminders(id, listOf(relative(30), relative(0)))
        assertEquals(at("2026-10-24T14:30"), alarm.scheduledAt)
    }

    @Test
    fun allDayTask_firesDaysBeforeAtMinuteOfDay() = runTest {
        val id = interactor.add("Rent", dueAt = at("2026-10-26T00:00"), allDay = true)
        interactor.setReminders(id, listOf(relative(Reminder.DAY_MINUTES, minuteOfDay = 20 * 60)))
        assertEquals(at("2026-10-25T20:00"), alarm.scheduledAt)
    }

    @Test
    fun pastReminders_areNotScheduled_andDuplicatesCollapse() = runTest {
        val id = interactor.add("Old", dueAt = at("2026-10-24T07:00"), allDay = false)
        interactor.setReminders(id, listOf(relative(0), relative(0)))
        assertEquals(1, reminders.all.size)
        assertNull(alarm.scheduledAt)
    }

    @Test
    fun onAlarm_showsDue_marksFired_andArmsNext() = runTest {
        val a = interactor.add("A", dueAt = at("2026-10-24T09:00"), allDay = false)
        val b = interactor.add("B", dueAt = at("2026-10-24T10:00"), allDay = false)
        interactor.setReminders(a, listOf(relative(0)))
        interactor.setReminders(b, listOf(relative(0)))

        clock.set(LocalDateTime.of(2026, 10, 24, 9, 0))
        scheduler.onAlarm()
        assertEquals(listOf(a), notifier.shown.map { it.first })
        assertEquals(at("2026-10-24T10:00"), alarm.scheduledAt)

        // Recomputing afterwards (any task change) must not bring the fired reminder back.
        scheduler.syncTask(a)
        assertNull(reminders.getForOwner(ReminderOwner.TASK, a).single().nextFireAt)
        assertEquals(at("2026-10-24T10:00"), alarm.scheduledAt)
    }

    @Test
    fun doneOrTrashedTasks_doNotNotify_andCancelVisible() = runTest {
        val id = interactor.add("A", dueAt = at("2026-10-24T09:00"), allDay = false)
        interactor.setReminders(id, listOf(relative(0)))
        notifier.visible += id
        interactor.setDone(id, true)
        assertTrue(id !in notifier.visible)
        assertNull(alarm.scheduledAt)

        interactor.setDone(id, false)
        assertEquals(at("2026-10-24T09:00"), alarm.scheduledAt)
        interactor.moveToTrash(id)
        assertNull(alarm.scheduledAt)
    }

    @Test
    fun snooze_firesAgainLater() = runTest {
        val id = interactor.add("A", dueAt = at("2026-10-24T09:00"), allDay = false)
        interactor.setReminders(id, listOf(relative(0)))
        clock.set(LocalDateTime.of(2026, 10, 24, 9, 0))
        scheduler.onAlarm()
        val reminderId = notifier.shown.single().second

        scheduler.snooze(reminderId)
        assertEquals(clock.nowMs + 10 * MINUTE_MS, alarm.scheduledAt)
        clock.advance(10 * MINUTE_MS)
        scheduler.onAlarm()
        assertEquals(2, notifier.shown.size)
        assertNull(alarm.scheduledAt)
    }

    @Test
    fun movingDueTime_overridesSnooze_butOtherEditsKeepIt() = runTest {
        val id = interactor.add("A", dueAt = at("2026-10-24T09:00"), allDay = false)
        interactor.setReminders(id, listOf(relative(0)))
        clock.set(LocalDateTime.of(2026, 10, 24, 9, 0))
        scheduler.onAlarm()
        scheduler.snooze(notifier.shown.single().second)
        val snoozed = clock.nowMs + 10 * MINUTE_MS

        interactor.save(tasks.get(id)!!.copy(title = "Renamed"))
        assertEquals(snoozed, alarm.scheduledAt)

        interactor.save(tasks.get(id)!!.copy(dueAt = at("2026-10-24T09:05")))
        assertEquals(at("2026-10-24T09:05"), alarm.scheduledAt)
        assertNull(reminders.getForOwner(ReminderOwner.TASK, id).single().snoozedUntil)
    }

    @Test
    fun repeatCompletion_rearmsForNextOccurrence() = runTest {
        val id = interactor.add("Daily", dueAt = at("2026-10-24T09:00"), allDay = false)
        interactor.save(tasks.get(id)!!.copy(repeatRule = "FREQ=DAILY"))
        interactor.setReminders(id, listOf(relative(0)))
        interactor.setDone(id, true)
        assertEquals(at("2026-10-25T09:00"), alarm.scheduledAt)
    }

    @Test
    fun missedWhileOff_firesOnReschedule_butZoneRecomputeDropsPast() = runTest {
        val id = interactor.add("A", dueAt = at("2026-10-24T09:00"), allDay = false)
        interactor.setReminders(id, listOf(relative(0)))
        clock.set(LocalDateTime.of(2026, 10, 24, 11, 0))

        scheduler.rescheduleAll(recompute = false)
        assertEquals(at("2026-10-24T09:00"), alarm.scheduledAt)
        scheduler.rescheduleAll(recompute = true)
        assertNull(alarm.scheduledAt)
    }

    @Test
    fun permanentDelete_removesReminders() = runTest {
        val id = interactor.add("A", dueAt = at("2026-10-24T09:00"), allDay = false)
        interactor.setReminders(id, listOf(relative(0)))
        interactor.moveToTrash(id)
        interactor.deletePermanently(id)
        assertTrue(reminders.all.isEmpty())
    }
}
