package com.meolist.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.meolist.core.locale.localized

/** Notification channels. Called on every app start so names follow the current language. */
object NotificationChannels {
    const val REMINDERS = "reminders"
    const val TIMERS = "timers"
    const val GOALS = "goals"

    fun create(context: Context) {
        val context = context.localized()
        val manager = context.getSystemService(NotificationManager::class.java)
        val reminders = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_reminders_desc)
            enableVibration(true)
        }
        // Ongoing timers update silently and must never make a sound.
        val timers = NotificationChannel(
            TIMERS,
            context.getString(R.string.notif_channel_timers),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_timers_desc)
            setShowBadge(false)
        }
        val goals = NotificationChannel(
            GOALS,
            context.getString(R.string.notif_channel_goals),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_goals_desc)
        }
        manager.createNotificationChannels(listOf(reminders, timers, goals))
    }
}
