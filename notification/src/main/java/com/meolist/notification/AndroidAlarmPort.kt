package com.meolist.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.meolist.domain.reminder.AlarmPort
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
        val intent = alarmIntent()
        if (atMillis == null) {
            alarmManager.cancel(intent)
            return
        }
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
                return
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call.
            }
        }
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, atMillis, FALLBACK_WINDOW_MS, intent)
    }

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val FALLBACK_WINDOW_MS = 10 * 60_000L
    }
}
