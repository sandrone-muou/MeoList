package com.meolist.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meolist.domain.settings.*
import com.meolist.core.locale.AppLanguage
import com.meolist.core.locale.LanguageStore
import com.meolist.domain.tracker.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TimerAndNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val services get() = EntryPointAccessors.fromApplication(
        ApplicationProvider.getApplicationContext<Context>(), TimerTestServices::class.java,
    )
    private var original: AppSettings? = null
    private var createdType: Long? = null
    private var originalLanguage: AppLanguage? = null

    @Before fun rememberPreferences() {
        runBlocking { original = services.preferences().settings.first() }
        originalLanguage = LanguageStore.selected(compose.activity)
        val language = InstrumentationRegistry.getArguments().getString("language")
        if (language != null) {
            LanguageStore.save(compose.activity, if (language == "zh") AppLanguage.ZH else AppLanguage.EN)
            compose.activityRule.scenario.recreate()
        }
    }

    @After fun restoreFixtures() {
        runBlocking {
            createdType?.let { services.timers().deleteType(it) }
            original?.let { old -> services.preferences().updateSettings { old } }
        }
        LanguageStore.save(compose.activity, originalLanguage)
    }

    @Test fun optionalDestinationsBecomeRootsAndUnpinnedFarmRemainsReachable() {
        runBlocking { services.preferences().updateSettings { it.copy(bottomDestinations = listOf(
            BottomDestination.TRACKER, BottomDestination.TASKS, BottomDestination.STATS, BottomDestination.CALENDAR, BottomDestination.MORE,
        )) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("bottom-stats").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bottom-stats").performClick().assertIsSelected()
        compose.onNodeWithContentDescription(compose.activity.getString(com.meolist.core.R.string.core_back)).assertDoesNotExist()
        compose.onNodeWithTag("bottom-calendar").performClick().assertIsSelected()
        screenshot("custom-bottom-navigation")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("bottom-calendar").assertIsSelected()
        compose.onNodeWithTag("bottom-more").performClick()
        compose.onNodeWithTag("bottom-farm").assertDoesNotExist()
        compose.onNodeWithTag("more-route-${com.meolist.core.navigation.Routes.FARM}").performScrollTo().performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(com.meolist.core.R.string.core_back)).performClick()
        compose.onNodeWithTag("bottom-more").assertIsSelected()
        val settingsEntry = hasTestTag("more-route-${com.meolist.core.navigation.Routes.SETTINGS}")
        compose.onNodeWithTag("more-list").performScrollToNode(settingsEntry)
        compose.onNode(settingsEntry).performClick()
        val navigationEntry = hasText(compose.activity.getString(com.meolist.feature.settings.R.string.settings_bottom_navigation)) and hasClickAction()
        compose.onNodeWithTag("settings-list").performScrollToNode(navigationEntry)
        compose.onNode(navigationEntry).performClick()
        val reset = hasText(compose.activity.getString(com.meolist.feature.settings.R.string.settings_bottom_reset))
        compose.onNodeWithTag("bottom-settings-list").performScrollToNode(reset)
        compose.onNode(reset).performClick()
        val stats = hasText(compose.activity.getString(com.meolist.core.R.string.core_nav_stats))
        compose.onNodeWithTag("bottom-settings-list").performScrollToNode(stats)
        compose.onNode(stats).assertIsNotEnabled()
        compose.onNodeWithTag("bottom-settings-list").performScrollToNode(hasText(compose.activity.getString(com.meolist.feature.settings.R.string.settings_bottom_navigation_hint, 5)))
        screenshot("bottom-navigation-settings")
    }

    @Test fun markedStagesStayFixedAndRecordOpensItsOwnChart() {
        val uniqueName = "Stage UI test ${System.nanoTime()}"
        var plan: List<Long?> = emptyList()
        runBlocking {
            services.preferences().updateSettings { it.copy(ignoreShortRecordSec = 0, bottomDestinations = BottomNavigation.defaults) }
            val timers = services.timers()
            val type = timers.saveType(ActivityType(name = uniqueName, icon = "timer", color = 0xFF446688.toInt()), emptyList())
            createdType = type
            val first = timers.saveSubproject(ActivitySubproject(typeId = type, name = "Reading fixture", color = 0xFF446688.toInt()))
            val second = timers.saveSubproject(ActivitySubproject(typeId = type, name = "Practice fixture", color = 0xFF886644.toInt()))
            plan = listOf(first, second)
        }
        compose.onNodeWithTag("bottom-tracker").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(uniqueName).fetchSemanticsNodes().isNotEmpty() }
        val idleHeight = compose.onNodeWithTag("tracker-type-$createdType").fetchSemanticsNode().boundsInRoot.height
        screenshot("tracker-idle")
        runBlocking {
            val running = services.timers().start(requireNotNull(createdType), stagePlan = plan)
            services.timers().updateRunning(running, System.currentTimeMillis() - 600_000, "", null)
        }
        compose.waitForIdle()
        val runningHeight = compose.onNodeWithTag("tracker-type-$createdType").fetchSemanticsNode().boundsInRoot.height
        assertEquals(idleHeight.toDouble(), runningHeight.toDouble(), 1.0)
        screenshot("tracker-running")
        compose.onNodeWithText(uniqueName).performClick()
        compose.onNodeWithTag("session-total").assertIsDisplayed()
        compose.onNodeWithTag("completed-stage-1").assertDoesNotExist()
        compose.onNodeWithTag("session-mark").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("completed-stage-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("completed-stage-2").assertDoesNotExist()
        screenshot("session-marked")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("completed-stage-1").assertIsDisplayed()
        compose.onNodeWithTag("completed-stage-2").assertDoesNotExist()
        compose.onNodeWithTag("session-stop").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(compose.activity.getString(com.meolist.feature.tracker.R.string.tracker_session_edit))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("session-list").performScrollToNode(hasTestTag("session-chart"))
        compose.onNodeWithTag("session-chart").assertIsDisplayed()
        screenshot("session-chart")
        runBlocking {
            val saved = services.records().getAllRecords().filter { it.typeId == createdType }.single()
            assertEquals(saved.durationMs, saved.stages.sumOf { it.durationMs })
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val folder = File(compose.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
