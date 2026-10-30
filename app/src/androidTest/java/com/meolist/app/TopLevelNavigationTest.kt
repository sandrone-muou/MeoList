package com.meolist.app

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopLevelNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun tasksCanBeReenteredFromEveryTopLevelDestination() {
        val destinations = listOf(R.string.nav_tracker, R.string.nav_habits, R.string.nav_farm, R.string.nav_more)
        destinations.forEach { destination ->
            select(destination)
            select(R.string.nav_tasks)
        }
    }

    @Test fun repeatedSelectionKeepsTheCurrentDestination() {
        select(R.string.nav_tracker)
        select(R.string.nav_tracker)
        select(R.string.nav_tasks)
        select(R.string.nav_tasks)
    }

    private fun select(labelId: Int) {
        val label = compose.activity.getString(labelId)
        val node = compose.onAllNodes(hasText(label, substring = false) and hasClickAction()).onFirst()
        node.performClick()
        compose.waitForIdle()
        node.assertIsSelected()
    }
}
