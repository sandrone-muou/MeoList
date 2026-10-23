package com.meolist.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Where a notification tap should lead inside the app. */
sealed interface DeepLink {
    data class Task(val taskId: Long) : DeepLink
    data object Tracker : DeepLink
}

/** `meolist://` links used by notification content intents. */
object DeepLinks {
    private const val SCHEME = "meolist"

    fun taskUri(taskId: Long): Uri = Uri.parse("$SCHEME://task/$taskId")
    val trackerUri: Uri = Uri.parse("$SCHEME://tracker")

    /** The link carried by an intent that opened the app, or null. */
    fun from(intent: Intent?): DeepLink? {
        val data = intent?.data ?: return null
        if (data.scheme != SCHEME) return null
        return when (data.host) {
            "task" -> data.lastPathSegment?.toLongOrNull()?.let { DeepLink.Task(it) }
            "tracker" -> DeepLink.Tracker
            else -> null
        }
    }

    /** Opens the launcher activity with [uri]; distinct URIs give distinct PendingIntents. */
    internal fun openIntent(context: Context, uri: Uri): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        launch.action = Intent.ACTION_VIEW
        launch.data = uri
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
