package com.meolist.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meolist.domain.common.Clock
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.today
import com.meolist.domain.reminder.ReminderNotifier
import com.meolist.domain.task.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderNotifier {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // Checked through areNotificationsEnabled().
    override fun showTaskReminder(task: Task, reminderId: Long) {
        if (!manager.areNotificationsEnabled()) return
        val due = task.dueAt?.let { dueText(it, task.allDay) }
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(due ?: task.note.takeIf { it.isNotBlank() })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openTaskIntent(task.id))
            .addAction(0, context.getString(R.string.notif_action_complete), actionIntent(ReminderActionReceiver.ACTION_COMPLETE, task.id, reminderId))
            .addAction(0, context.getString(R.string.notif_action_snooze), actionIntent(ReminderActionReceiver.ACTION_SNOOZE, task.id, reminderId))
        if (task.note.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(due, task.note.trim()).joinToString("\n")))
        }
        try {
            manager.notify(TAG_TASK, notificationId(task.id), builder.build())
        } catch (_: SecurityException) {
            // Notification permission was revoked.
        }
    }

    override fun cancelTaskReminder(taskId: Long) {
        manager.cancel(TAG_TASK, notificationId(taskId))
    }

    private fun dueText(dueAt: Long, allDay: Boolean): String {
        val zone = clock.zone()
        val dateTime = dueAt.toLocalDateTime(zone)
        val date = dateTime.toLocalDate()
        val today = clock.today()
        val dateText = when (date) {
            today -> context.getString(R.string.notif_today)
            today.plusDays(1) -> context.getString(R.string.notif_tomorrow)
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
        return if (allDay) dateText else "$dateText ${dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    private fun openTaskIntent(taskId: Long): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        launch.action = Intent.ACTION_VIEW
        launch.data = taskUri(taskId)
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(action: String, taskId: Long, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            // Distinct data keeps one PendingIntent per task and action.
            .setData(Uri.parse("meolist://reminder/$taskId/$reminderId"))
            .putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val TAG_TASK = "task"
        const val TASK_URI_PREFIX = "meolist://task/"

        fun taskUri(taskId: Long): Uri = Uri.parse("$TASK_URI_PREFIX$taskId")

        /** Task id from a notification's open intent, or null. */
        fun taskIdFrom(intent: Intent?): Long? {
            val data = intent?.data ?: return null
            if (data.scheme != "meolist" || data.host != "task") return null
            return data.lastPathSegment?.toLongOrNull()
        }

        private fun notificationId(taskId: Long): Int = (taskId % Int.MAX_VALUE).toInt()
    }
}
