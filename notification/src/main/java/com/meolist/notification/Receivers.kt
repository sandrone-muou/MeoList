package com.meolist.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.reminder.AlarmPort
import com.meolist.domain.reminder.ReminderNotifier
import com.meolist.domain.reminder.ReminderScheduler
import com.meolist.domain.task.TaskInteractor
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds abstract fun alarmPort(impl: AndroidAlarmPort): AlarmPort
    @Binds abstract fun reminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
}

/** Dependencies for broadcast receivers and app startup, which are not Hilt entry points themselves. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationEntryPoint {
    fun reminderScheduler(): ReminderScheduler
    fun taskInteractor(): TaskInteractor
    fun reminderNotifier(): ReminderNotifier

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
        // Covers anything the system dropped: force-stops, missed broadcasts on restrictive ROMs.
        entryPoint.applicationScope().launch { entryPoint.reminderScheduler().rescheduleAll() }
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
        runAsync(context) { reminderScheduler().rescheduleAll(recompute) }
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
