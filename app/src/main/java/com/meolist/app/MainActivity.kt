package com.meolist.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.meolist.app.ui.MeoAppRoot
import com.meolist.core.theme.MeoTheme
import com.meolist.notification.AndroidReminderNotifier
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Task requested by a reminder notification, consumed once the UI has navigated to it. */
    private var openTaskId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a configuration change or process restore the back stack already holds the task.
        if (savedInstanceState == null) openTaskId = AndroidReminderNotifier.taskIdFrom(intent)
        setContent {
            MeoTheme {
                MeoAppRoot(openTaskId = openTaskId, onTaskOpened = { openTaskId = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AndroidReminderNotifier.taskIdFrom(intent)?.let { openTaskId = it }
    }
}
