package com.meolist.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Notification channels. Called on every app start so names follow the current language. */
object NotificationChannels {
    const val REMINDERS = "reminders"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val reminders = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_reminders_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(reminders)
    }
}
