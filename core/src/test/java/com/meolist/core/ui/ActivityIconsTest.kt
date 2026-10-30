package com.meolist.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ActivityIconsTest {
    @Test
    fun storedSportKeyResolvesToRoundedDumbbell() {
        val icon = ActivityIcons.of("sport")
        assertEquals("RoundedDumbbell", icon.name)
        assertEquals(24f, icon.viewportWidth, 0f)
        assertEquals(24f, icon.viewportHeight, 0f)
        assertEquals(1, ActivityIcons.all.count { it.first == "sport" })
        assertSame(icon, ActivityIcons.of("sport"))
    }

    @Test
    fun unknownKeysKeepTheTimerFallback() {
        assertSame(ActivityIcons.of(ActivityIcons.DEFAULT), ActivityIcons.of("unknown"))
    }
}
