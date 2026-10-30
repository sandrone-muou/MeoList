package com.meolist.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.reminder.AlarmPort
import com.meolist.domain.reminder.ReminderNotifier
import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.tracker.GoalAlarmPort
import com.meolist.domain.tracker.TimerInteractor
import com.meolist.domain.tracker.TimerNotifier
import com.meolist.domain.tracker.TimerSync
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds abstract fun alarmPort(impl: AndroidAlarmPort): AlarmPort
    @Binds abstract fun reminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
    @Binds abstract fun timerNotifier(impl: AndroidTimerNotifier): TimerNotifier
    @Binds abstract fun goalAlarmPort(impl: AndroidGoalAlarmPort): GoalAlarmPort
}

/** Dependencies for broadcast receivers and app startup, which are not Hilt entry points themselves. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationEntryPoint {
    fun reminderScheduler(): ReminderScheduler
    fun taskInteractor(): TaskInteractor
    fun reminderNotifier(): ReminderNotifier
    fun timerSync(): TimerSync
    fun timerInteractor(): TimerInteractor
    fun androidTimerNotifier(): AndroidTimerNotifier

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}

internal fun Context.notificationEntryPoint(): NotificationEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, NotificationEntryPoint::class.java)

/** Runs [block] off the main thread while keeping the receiver alive until it finishes. */
internal fun BroadcastReceiver.runAsync(context: Context, block: suspend NotificationEntryPoint.() -> Unit) {
    val pending = goAsync()
    val entryPoint = context.notificationEntryPoint()
    entryPoint.applicationScope().launch {
        try {
            entryPoint.block()
        } finally {
            pending.finish()
        }
    }
}

/** Entry point called from Application.onCreate. */
object NotificationStartup {
    fun onAppStart(context: Context) {
        NotificationChannels.create(context)
        val entryPoint = context.notificationEntryPoint()
        entryPoint.applicationScope().launch {
            entryPoint.reminderScheduler().rescheduleAll()
        }
    }

    fun onAppForeground(context: Context) {
        val entryPoint = context.notificationEntryPoint()
        // A deleteIntent can start the process too; only a foreground launch should restore timers.
        entryPoint.applicationScope().launch {
            entryPoint.timerSync().sync()
        }
    }
}

/** Fires when the reminder alarm goes off. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        runAsync(context) { reminderScheduler().onAlarm() }
    }

    companion object {
        const val ACTION_FIRE = "com.meolist.action.REMINDER_ALARM"
    }
}

/** Re-arms reminders after events that clear or invalidate alarms. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recompute = when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED -> true
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> false
            else -> return
        }
        runAsync(context) {
            reminderScheduler().rescheduleAll(recompute)
            timerSync().sync()
        }
    }
}

/** Handles the "Complete" and "Snooze" buttons of a reminder notification. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        if (taskId < 0) return
        when (intent.action) {
            ACTION_COMPLETE -> runAsync(context) {
                taskInteractor().setDone(taskId, true)
                reminderNotifier().cancelTaskReminder(taskId)
            }
            ACTION_SNOOZE -> runAsync(context) {
                if (reminderId >= 0) reminderScheduler().snooze(reminderId) else reminderNotifier().cancelTaskReminder(taskId)
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.meolist.action.REMINDER_COMPLETE"
        const val ACTION_SNOOZE = "com.meolist.action.REMINDER_SNOOZE"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_REMINDER_ID = "reminderId"
    }
}

/** Fires when a running timer should have reached its goal. */
class GoalAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        runAsync(context) { timerSync().sync() }
    }

    companion object {
        const val ACTION_FIRE = "com.meolist.action.GOAL_ALARM"
    }
}

class TimerDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS) return
        val ids = intent.getLongArrayExtra(EXTRA_RUNNING_IDS)?.filter { it > 0 }?.toSet().orEmpty()
        if (ids.isEmpty()) return
        // Register the dismissal before yielding so an in-flight sync cannot repost the notification.
        val entryPoint = context.notificationEntryPoint()
        entryPoint.androidTimerNotifier().markDismissed(ids)
        val pending = goAsync()
        entryPoint.applicationScope().launch {
            try {
                withContext(Dispatchers.IO) { entryPoint.androidTimerNotifier().persistDismissed() }
                entryPoint.timerSync().sync()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.meolist.action.TIMER_DISMISS"
        const val EXTRA_RUNNING_IDS = "runningIds"
    }
}

/** Handles the "Stop" button of a timer notification. */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runningId = intent.getLongExtra(EXTRA_RUNNING_ID, -1)
        if (intent.action != ACTION_STOP || runningId < 0) return
        runAsync(context) {
            // A timer that was already stopped elsewhere only needs its notification removed.
            timerInteractor().stop(runningId)
            timerSync().sync()
        }
    }

    companion object {
        const val ACTION_STOP = "com.meolist.action.TIMER_STOP"
        const val EXTRA_RUNNING_ID = "runningId"
    }
}
