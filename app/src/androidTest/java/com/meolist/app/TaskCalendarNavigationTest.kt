package com.meolist.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.meolist.feature.tasks.R as TasksR
import com.meolist.feature.calendar.R as CalendarR

@RunWith(AndroidJUnit4::class)
class TaskCalendarNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun calendarDoesNotReplaceListFilterAndSurvivesRecreationAndTabNavigation() {
        compose.onNodeWithTag("task-filter").performClick()
        compose.onNode(hasText(text(TasksR.string.tasks_filter_all)) and hasClickAction()).performClick()
        compose.onNodeWithTag("task-view-CALENDAR").performClick()
        compose.onNodeWithContentDescription(text(com.meolist.core.R.string.core_calendar_next)).performClick()
        val selectedDay = com.meolist.core.ui.longDate(
            java.time.LocalDate.now().plusMonths(1), compose.activity.resources.configuration.locales[0],
        )
        compose.onNodeWithContentDescription(selectedDay).assertIsSelected()
        compose.onNode(hasText(text(com.meolist.core.R.string.core_calendar_week)) and hasClickAction()).performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("task-view-CALENDAR").assertIsSelected()

        compose.onNodeWithContentDescription(selectedDay).assertIsSelected()
        compose.onNode(hasText(text(com.meolist.core.R.string.core_calendar_week)) and hasClickAction()).assertIsSelected()
        selectTab(R.string.nav_more)
        selectTab(R.string.nav_tasks)
        compose.onNodeWithTag("task-view-CALENDAR").assertIsSelected()
        compose.onNodeWithContentDescription(selectedDay).assertIsSelected()
        compose.onNodeWithTag("task-view-LIST").performClick()
        compose.onNodeWithTag("task-filter").assertIsDisplayed()
        compose.onNode(hasText(text(TasksR.string.tasks_filter_all)) and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun bothCalendarEntryPointsOfferTaskCreationAndCancelReturnsToCalendar() {
        compose.onNodeWithTag("task-view-CALENDAR").performClick()
        compose.onNodeWithTag("task-new").performClick()
        waitForEntry()
        compose.onNodeWithContentDescription(text(com.meolist.core.R.string.core_cancel)).performClick()
        selectTab(R.string.nav_more)
        compose.onNode(hasText(text(R.string.more_calendar)) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription(text(CalendarR.string.calendar_add_task)).performClick()
        waitForEntry()
        compose.onNodeWithContentDescription(text(com.meolist.core.R.string.core_back)).performClick()
        compose.onNodeWithContentDescription(text(CalendarR.string.calendar_add_task)).assertIsDisplayed()
    }

    private fun selectTab(id: Int) {
        compose.onAllNodes(hasText(text(id)) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun waitForEntry() {
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("task-entry-title").assertIsDisplayed() }.isSuccess
        }
    }
}
