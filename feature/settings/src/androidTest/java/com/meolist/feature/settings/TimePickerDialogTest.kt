package com.meolist.feature.settings

import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meolist.core.ui.MeoTimePickerDialog
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimePickerDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun confirmsMidnight() = confirmUnchanged(LocalTime.MIDNIGHT)

    @Test
    fun confirmsNoon() = confirmUnchanged(LocalTime.NOON)

    @Test
    fun confirmsLastMinuteInSystemHourFormat() = confirmUnchanged(LocalTime.of(23, 59))

    @Test
    fun wheelReachesMinuteFiftyNine() {
        var saved: LocalTime? = null
        compose.setContent {
            MaterialTheme {
                MeoTimePickerDialog(LocalTime.of(9, 0), onConfirm = { saved = it }, onDismiss = {})
            }
        }
        compose.onAllNodes(hasScrollToIndexAction())[1].performScrollToIndex(59)
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).performClick()
        compose.runOnIdle {
            assertEquals(9, saved!!.hour)
            assertEquals(59, saved.minute)
        }
    }

    private fun confirmUnchanged(initial: LocalTime) {
        var saved: LocalTime? = null
        compose.setContent {
            MaterialTheme {
                MeoTimePickerDialog(initial, onConfirm = { saved = it }, onDismiss = {})
            }
        }
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).performClick()
        compose.runOnIdle { assertEquals(initial, saved) }
    }

    @Test
    fun editedMinuteSurvivesModeSwitchAndSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var saved: LocalTime? = null
        restoration.setContent {
            MaterialTheme {
                MeoTimePickerDialog(LocalTime.of(15, 30), onConfirm = { saved = it }, onDismiss = {})
            }
        }
        switchToInput()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("04")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("57")
        compose.onNodeWithText(text(com.meolist.core.R.string.core_time_picker_wheel)).performClick()
        switchToInput()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).performClick()
        val expectedHour = if (DateFormat.is24HourFormat(context)) 4 else 16
        compose.runOnIdle { assertEquals(LocalTime.of(expectedHour, 57), saved) }
    }

    @Test
    fun invalidMinuteDisablesConfirmationAndWheelSwitch() {
        compose.setContent {
            MaterialTheme {
                MeoTimePickerDialog(LocalTime.of(9, 0), onConfirm = {}, onDismiss = {})
            }
        }
        switchToInput()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("75")
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).assertIsNotEnabled()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_time_picker_wheel)).assertIsNotEnabled()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_time_picker_invalid_minute)).assertIsDisplayed()
    }

    @Test
    fun cancellingEditedTimeNeverConfirms() {
        var dismissed = false
        var saves = 0
        compose.setContent {
            MaterialTheme {
                MeoTimePickerDialog(LocalTime.of(9, 0), onConfirm = { saves++ }, onDismiss = { dismissed = true })
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
    fun clearButtonUsesClearCallbackWithoutConfirming() {
        var cleared = false
        var saves = 0
        compose.setContent {
            MaterialTheme {
                MeoTimePickerDialog(
                    LocalTime.of(9, 0),
                    onConfirm = { saves++ },
                    onDismiss = {},
                    onClear = { cleared = true },
                )
            }
        }
        compose.onNodeWithText(text(com.meolist.core.R.string.core_clear)).performClick()
        compose.runOnIdle {
            assertTrue(cleared)
            assertEquals(0, saves)
        }
    }

    @Test
    fun largeFontKeepsActionsReachableInEitherMode() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                MaterialTheme {
                    MeoTimePickerDialog(LocalTime.of(9, 0), onConfirm = {}, onDismiss = {})
                }
            }
        }
        compose.onNodeWithText(text(com.meolist.core.R.string.core_cancel)).assertIsDisplayed()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).assertIsDisplayed()
        switchToInput()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_cancel)).assertIsDisplayed()
        compose.onNodeWithText(text(com.meolist.core.R.string.core_ok)).assertIsDisplayed()
    }

    private fun switchToInput() {
        compose.onNodeWithText(text(com.meolist.core.R.string.core_time_picker_input)).performClick()
    }
}
