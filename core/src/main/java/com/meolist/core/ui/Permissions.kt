package com.meolist.core.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.meolist.core.R

fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

/** Opens the system screen where the user allows this app to set exact alarms (Android 12+). */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    context.startSafely(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
    )
}

fun openNotificationSettings(context: Context) {
    context.startSafely(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** What reminders need from the system, re-read whenever the screen resumes. */
@Stable
class ReminderPermissionState internal constructor(notifications: Boolean, exactAlarms: Boolean) {
    var notificationsEnabled by mutableStateOf(notifications)
        internal set
    var exactAlarmsAllowed by mutableStateOf(exactAlarms)
        internal set
    internal var requestNotifications: () -> Unit = {}

    /** Asks for the notification permission (Android 13+) unless notifications already work. */
    fun requestNotificationsIfNeeded() {
        if (!notificationsEnabled) requestNotifications()
    }
}

@Composable
fun rememberReminderPermissionState(): ReminderPermissionState {
    val context = LocalContext.current
    val state = remember { ReminderPermissionState(notificationsEnabled(context), canScheduleExactAlarms(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.notificationsEnabled = granted || notificationsEnabled(context)
    }
    state.requestNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }
    LifecycleResumeEffect(state) {
        state.notificationsEnabled = notificationsEnabled(context)
        state.exactAlarmsAllowed = canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }
    return state
}

/**
 * Explains why reminders may not show up on time and links to the fix. Shows nothing when
 * notifications and exact alarms are both allowed.
 */
@Composable
fun ReminderPermissionBanner(state: ReminderPermissionState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (message, action, onClick) = when {
        !state.notificationsEnabled -> Triple(
            R.string.core_perm_notifications_off,
            R.string.core_perm_allow,
            { openNotificationSettings(context) },
        )
        !state.exactAlarmsAllowed -> Triple(
            R.string.core_perm_exact_alarm_off,
            R.string.core_perm_allow,
            { openExactAlarmSettings(context) },
        )
        else -> return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(action))
            }
        }
    }
}
