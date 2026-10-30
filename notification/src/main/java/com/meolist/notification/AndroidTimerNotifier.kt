package com.meolist.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.io.File
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meolist.core.locale.localized
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.tracker.ActivityGoal
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.RunningTimerView
import com.meolist.domain.tracker.TimerNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * One ongoing notification per running timer. The system chronometer counts up from the stored start,
 * so nothing has to run in the background and no foreground service is needed.
 */
@Singleton
class AndroidTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : TimerNotifier {
    private val manager = NotificationManagerCompat.from(context)
    private val systemManager = context.getSystemService(NotificationManager::class.java)
    private val state by lazy { TimerNotificationState(File(context.noBackupFilesDir, "dismissed_timers")) }

    @Synchronized
    @SuppressLint("MissingPermission")
    override fun showRunning(timers: List<RunningTimerView>) {
        val context = context.localized()
        val visible = state.visible(timers)
        val ids = visible.map { notificationId(it.timer.id) }.toSet()
        // Cancelling a summary can take its children along, so remove it before posting children.
        if (visible.size < 2) manager.cancel(TAG_SUMMARY, SUMMARY_ID)
        systemManager.activeNotifications
            .filter { it.tag == TAG_TIMER && it.id !in ids }
            .forEach { manager.cancel(TAG_TIMER, it.id) }
        if (!manager.areNotificationsEnabled()) {
            persist()
            return
        }
        try {
            // Demote the previous candidate before requesting promotion for the new one.
            visible.drop(1).forEach { timer ->
                manager.notify(TAG_TIMER, notificationId(timer.timer.id), timerNotification(context, timer, promote = false))
            }
            visible.firstOrNull()?.let { timer ->
                manager.notify(TAG_TIMER, notificationId(timer.timer.id), timerNotification(context, timer, promote = true))
            }
            if (visible.size >= 2) manager.notify(TAG_SUMMARY, SUMMARY_ID, summary(context, visible))
        } catch (_: SecurityException) {
            // Notification permission was revoked.
        }
        persist()
    }

    /** Marks the timers dismissed on the caller's thread so a concurrent sync cannot repost them. */
    @Synchronized
    fun markDismissed(ids: Set<Long>) {
        state.mark(ids)
        ids.forEach { manager.cancel(TAG_TIMER, notificationId(it)) }
    }

    @Synchronized
    fun persistDismissed() {
        state.persist()
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) { persistDismissed() }
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

    internal fun timerNotification(context: Context, view: RunningTimerView, promote: Boolean): Notification {
        val notification = NotificationCompat.Builder(context, NotificationChannels.TIMERS)
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(view.type.color)
            .setColorized(false)
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
            .setDeleteIntent(dismissIntent(listOf(view.timer.id)))
            .addAction(0, context.getString(R.string.notif_action_stop), stopIntent(view.timer.id))
            .build()
        return if (Build.VERSION.SDK_INT >= 36 && promote && systemManager.canPostPromotedNotifications()) {
            // SDK 36 supports the public extra but does not yet expose the builder shortcut.
            Notification.Builder.recoverBuilder(context, notification)
                .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
                .build()
        } else notification
    }

    internal fun summary(context: Context, timers: List<RunningTimerView>) =
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
            .setDeleteIntent(dismissIntent(timers.map { it.timer.id }))
            .build()

    private fun dismissIntent(ids: List<Long>): PendingIntent {
        val intent = Intent(context, TimerDismissReceiver::class.java)
            .setAction(TimerDismissReceiver.ACTION_DISMISS)
            .setData(Uri.parse("meolist://timer-dismiss/${ids.sorted().joinToString(",")}"))
            .putExtra(TimerDismissReceiver.EXTRA_RUNNING_IDS, ids.toLongArray())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

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
