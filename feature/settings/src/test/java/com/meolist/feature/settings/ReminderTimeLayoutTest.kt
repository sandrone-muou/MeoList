package com.meolist.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimeLayoutTest {
    @Test
    fun narrowLargeFontAndLandscapeDefaultToInput() {
        assertTrue(preferReminderTimeInput(336f, 720f, 1f))
        assertTrue(preferReminderTimeInput(381f, 720f, 1.3f))
        assertTrue(preferReminderTimeInput(720f, 336f, 1f))
        assertTrue(preferReminderTimeInput(381f, 400f, 1f))
    }

    @Test
    fun roomyNormalFontCanUseDial() {
        assertFalse(preferReminderTimeInput(381f, 720f, 1f))
        assertFalse(preferReminderTimeInput(440f, 720f, 1f))
    }
}
