package com.meolist.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meolist.core.locale.localized
import com.meolist.domain.tracker.ActivityGoal
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.RunningTimerView
import com.meolist.domain.tracker.TimerNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One ongoing notification per running timer. The system chronometer counts up from the stored start,
 * so nothing has to run in the background and no foreground service is needed.
 */
@Singleton
class AndroidTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : TimerNotifier {
    private val manager = NotificationManagerCompat.from(context)
    private val systemManager = context.getSystemService(NotificationManager::class.java)

    @SuppressLint("MissingPermission") // Checked through areNotificationsEnabled().
    override fun showRunning(timers: List<RunningTimerView>) {
        val context = context.localized()
        val ids = timers.map { notificationId(it.timer.id) }.toSet()
        // Removing the summary first: cancelling a summary can take its children along.
        if (timers.size < 2) manager.cancel(TAG_SUMMARY, SUMMARY_ID)
        systemManager.activeNotifications
            .filter { it.tag == TAG_TIMER && it.id !in ids }
            .forEach { manager.cancel(TAG_TIMER, it.id) }
        if (!manager.areNotificationsEnabled()) return
        try {
            timers.forEach { manager.notify(TAG_TIMER, notificationId(it.timer.id), timerNotification(context, it)) }
            if (timers.size >= 2) manager.notify(TAG_SUMMARY, SUMMARY_ID, summary(context, timers))
        } catch (_: SecurityException) {
            // Notification permission was revoked.
        }
    }

    @SuppressLint("MissingPermission")
    override fun showGoalReached(type: ActivityType, goal: ActivityGoal) {
        if (!manager.areNotificationsEnabled()) return
        val context = context.localized()
        val textRes = when (goal.period) {
            GoalPeriod.DAY -> R.string.notif_goal_day
            GoalPeriod.WEEK -> R.string.notif_goal_week
            GoalPeriod.MONTH -> R.string.notif_goal_month
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.GOALS)
            .setSmallIcon(R.drawable.ic_goal)
            .setColor(type.color)
            .setContentTitle(context.getString(R.string.notif_goal_title, type.name))
            .setContentText(context.getString(textRes, durationText(context, goal.targetSec)))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(DeepLinks.openIntent(context, DeepLinks.trackerUri))
            .build()
        try {
            manager.notify(TAG_GOAL, (type.id * 3 + goal.period.value).toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun timerNotification(context: Context, view: RunningTimerView) =
        NotificationCompat.Builder(context, NotificationChannels.TIMERS)
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(view.type.color)
            .setContentTitle(view.type.name)
            .setContentText(
                view.taskTitle?.takeIf { it.isNotBlank() }
                    ?: view.timer.comment.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.notif_timer_running),
            )
            .setWhen(view.timer.startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setGroup(GROUP)
            .setSortKey(view.timer.startedAt.toString().padStart(20, '0'))
            .setContentIntent(DeepLinks.openIntent(context, DeepLinks.trackerUri))
            .addAction(0, context.getString(R.string.notif_action_stop), stopIntent(view.timer.id))
            .build()

    private fun summary(context: Context, timers: List<RunningTimerView>) =
        NotificationCompat.Builder(context, NotificationChannels.TIMERS)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.notif_timers_summary, timers.size))
            .setContentText(timers.joinToString(" · ") { it.type.name })
            .setStyle(
                NotificationCompat.InboxStyle().also { style -> timers.forEach { style.addLine(it.type.name) } },
            )
            .setOngoing(true)
            .setSilent(true)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setContentIntent(DeepLinks.openIntent(context, DeepLinks.trackerUri))
            .build()

    private fun stopIntent(runningId: Long): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java)
            .setAction(TimerActionReceiver.ACTION_STOP)
            // Distinct data keeps one PendingIntent per timer.
            .setData(Uri.parse("meolist://timer/$runningId"))
            .putExtra(TimerActionReceiver.EXTRA_RUNNING_ID, runningId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun durationText(context: Context, seconds: Long): String {
        val h = (seconds / 3600).toInt()
        val m = ((seconds % 3600) / 60).toInt()
        return when {
            h == 0 -> context.getString(R.string.notif_duration_m, m)
            m == 0 -> context.getString(R.string.notif_duration_h, h)
            else -> context.getString(R.string.notif_duration_hm, h, m)
        }
    }

    private companion object {
        const val TAG_TIMER = "timer"
        const val TAG_SUMMARY = "timer_summary"
        const val TAG_GOAL = "goal"
        const val SUMMARY_ID = 0
        const val GROUP = "com.meolist.TIMERS"

        fun notificationId(runningId: Long): Int = (runningId % Int.MAX_VALUE).toInt()
    }
}
