package com.meolist.feature.settings

import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderTimeDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun confirmsMidnight() = confirmUnchanged(0)

    @Test
    fun confirmsNoon() = confirmUnchanged(12 * 60)

    @Test
    fun confirmsLastMinuteInSystemHourFormat() = confirmUnchanged(23 * 60 + 59)

    private fun confirmUnchanged(initial: Int) {
        var saved: Int? = null
        compose.setContent {
            MaterialTheme { ReminderTimeDialog(initial, onConfirm = { saved = it }, onDismiss = {}) }
        }
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).performClick()
        compose.runOnIdle { assertEquals(initial, saved) }
    }

    @Test
    fun editedTimeSurvivesModeSwitchAndSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var saved: Int? = null
        restoration.setContent {
            MaterialTheme { ReminderTimeDialog(15 * 60 + 30, onConfirm = { saved = it }, onDismiss = {}) }
        }
        switchToInput()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("04")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("45")
        compose.onNodeWithContentDescription(text(R.string.settings_time_switch_picker)).performClick()
        compose.onNodeWithContentDescription(text(R.string.settings_time_switch_input)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).performClick()
        val expectedHour = if (DateFormat.is24HourFormat(context)) 4 else 16
        compose.runOnIdle { assertEquals(expectedHour * 60 + 45, saved) }
    }

    @Test
    fun cancellingEditedTimeNeverConfirms() {
        var dismissed = false
        var saves = 0
        compose.setContent {
            MaterialTheme {
                ReminderTimeDialog(9 * 60, onConfirm = { saves++ }, onDismiss = { dismissed = true })
            }
        }
        switchToInput()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("59")
        compose.onNodeWithText(text(com.meolist.core.R.string.core_cancel)).performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertEquals(0, saves)
        }
    }

    @Test
    fun largeFontKeepsActionsReachableInEitherMode() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                MaterialTheme { ReminderTimeDialog(9 * 60, onConfirm = {}, onDismiss = {}) }
            }
        }
        switchToInput()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_cancel)).assertIsDisplayed()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).assertIsDisplayed()
        val toPicker = compose.onAllNodesWithContentDescription(text(R.string.settings_time_switch_picker))
        val toInput = compose.onAllNodesWithContentDescription(text(R.string.settings_time_switch_input))
        if (toPicker.fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(text(R.string.settings_time_switch_picker)).assertIsDisplayed()
        }
        if (toInput.fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(text(R.string.settings_time_switch_input)).assertIsDisplayed()
        }
        assertTrue(toPicker.fetchSemanticsNodes().isNotEmpty() || toInput.fetchSemanticsNodes().isNotEmpty())
    }

    private fun switchToInput() {
        val switch = text(R.string.settings_time_switch_input)
        if (compose.onAllNodesWithContentDescription(switch).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(switch).performClick()
        }
    }
}
