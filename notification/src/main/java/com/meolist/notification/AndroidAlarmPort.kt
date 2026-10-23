package com.meolist.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.meolist.domain.reminder.AlarmPort
import com.meolist.domain.tracker.GoalAlarmPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms the single reminder alarm. Uses an exact, Doze-safe alarm when the user allows exact alarms;
 * otherwise falls back to a 10-minute window, which may be deferred while the device is idle.
 */
@Singleton
class AndroidAlarmPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmPort {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(atMillis: Long?) {
        alarmManager.scheduleOrCancel(atMillis, alarmIntent())
    }

    override fun canScheduleExact(): Boolean = alarmManager.canExact()

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_REMINDER,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Arms the alarm for the moment a running timer reaches its goal. */
@Singleton
class AndroidGoalAlarmPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : GoalAlarmPort {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(atMillis: Long?) {
        alarmManager.scheduleOrCancel(atMillis, alarmIntent())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_GOAL,
        Intent(context, GoalAlarmReceiver::class.java).setAction(GoalAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

private const val REQUEST_REMINDER = 0
private const val REQUEST_GOAL = 1
private const val FALLBACK_WINDOW_MS = 10 * 60_000L

private fun AlarmManager.canExact(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()

private fun AlarmManager.scheduleOrCancel(atMillis: Long?, intent: PendingIntent) {
    if (atMillis == null) {
        cancel(intent)
        return
    }
    if (canExact()) {
        try {
            setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            return
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
    setWindow(AlarmManager.RTC_WAKEUP, atMillis, FALLBACK_WINDOW_MS, intent)
}
