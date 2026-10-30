package com.meolist.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.RunningTimerView
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun createChannels() {
        NotificationChannels.create(context)
        if (Build.VERSION.SDK_INT >= 33) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test fun standardNotificationKeepsChronometerAndActions() {
        val timer = timer(1)
        val notification = notifier().timerNotification(context, timer, false)
        assertEquals(timer.timer.startedAt, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.deleteIntent)
        assertEquals(1, notification.actions.size)
        assertNotNull(notification.actions.single().actionIntent)
    }

    @Test fun promotionUsesPublicExtraOnlyWhenSupportedAndAllowed() {
        val notification = notifier().timerNotification(context, timer(1), true)
        val allowed = Build.VERSION.SDK_INT >= 36 && manager.canPostPromotedNotifications()
        assertEquals(allowed, notification.extras.getBoolean("android.requestPromotedOngoing"))
        if (Build.VERSION.SDK_INT >= 36 && allowed) {
            assertTrue(notification.hasPromotableCharacteristics())
        }
        assertFalse(notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
    }

    @Test fun summaryNeverRequestsPromotionAndHasSeparateDismissAction() {
        val notifier = notifier()
        val first = notifier.summary(context, listOf(timer(1), timer(2)))
        val next = notifier.summary(context, listOf(timer(1), timer(2), timer(3)))
        assertTrue(first.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertFalse(first.extras.getBoolean("android.requestPromotedOngoing"))
        assertNotNull(first.deleteIntent)
        assertFalse(first.deleteIntent == next.deleteIntent)
    }

    @Test fun dismissalSurvivesRecreationAndPrunesFinishedSessions() {
        val file = File.createTempFile("dismissed-timer-test", ".txt", context.cacheDir)
        try {
            val timers = listOf(timer(1), timer(2))
            TimerNotificationState(file).apply {
                mark(setOf(1))
                mark(setOf(1))
                persist()
            }
            val restored = TimerNotificationState(file)
            assertEquals(listOf(2L), restored.visible(timers).map { it.timer.id })
            assertEquals(listOf(2L), restored.visible(listOf(timer(2))).map { it.timer.id })
            restored.persist()
            assertEquals(listOf(1L, 2L), TimerNotificationState(file).visible(timers).map { it.timer.id })
        } finally {
            file.delete()
            File(file.path + ".bak").delete()
            File(file.path + ".new").delete()
        }
    }

    @Test fun dismissalSnapshotDoesNotSuppressNewTimer() {
        val file = File.createTempFile("dismissed-timer-test", ".txt", context.cacheDir)
        try {
            val state = TimerNotificationState(file)
            state.mark(setOf(1, 2))
            state.persist()
            assertEquals(listOf(3L), state.visible(listOf(timer(1), timer(2), timer(3))).map { it.timer.id })
            state.persist()
            assertEquals(listOf(3L), state.visible(listOf(timer(3))).map { it.timer.id })
        } finally {
            file.delete()
        }
    }

    private fun notifier() = AndroidTimerNotifier(context, scope)

    private fun timer(id: Long) = RunningTimerView(
        RunningTimer(id = id, typeId = id, startedAt = System.currentTimeMillis() - 60_000 + id),
        ActivityType(id = id, name = "Activity $id", icon = "timer", color = 0xFF4A6382.toInt()),
        null,
    )
}
