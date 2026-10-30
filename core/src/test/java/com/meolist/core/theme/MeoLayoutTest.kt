package com.meolist.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeoLayoutTest {

    @Test
    fun phoneWidthsAreCompact() {
        listOf(320, 360, 405, 411, 599).forEach { width ->
            assertTrue("$width dp should be compact", MeoLayout.forWidth(width).compact)
        }
    }

    @Test
    fun tabletAndLandscapeWidthsKeepRegularMetrics() {
        listOf(600, 720, 840).forEach { width ->
            assertFalse("$width dp should be regular", MeoLayout.forWidth(width).compact)
        }
    }

    @Test
    fun compactMetricsAreSmallerThanRegular() {
        val regular = MeoLayout()
        val compact = MeoLayout.Compact
        assertTrue(compact.cardPadH < regular.cardPadH)
        assertTrue(compact.cardPadV < regular.cardPadV)
        assertTrue(compact.sectionTop < regular.sectionTop)
        assertTrue(compact.barChartHeight < regular.barChartHeight)
        assertTrue(compact.weekChartHeight < regular.weekChartHeight)
        assertTrue(compact.heatmapCell < regular.heatmapCell)
        assertTrue(compact.plotTileMin < regular.plotTileMin)
        assertTrue(compact.toolCardHeight < regular.toolCardHeight)
        assertTrue(compact.calendarCell < regular.calendarCell)
        assertTrue(compact.trackerPad < regular.trackerPad)
    }

    @Test
    fun regularMetricsMatchPreviousLayout() {
        val regular = MeoLayout()
        assertEquals(16f, regular.cardPadH.value)
        assertEquals(20f, regular.sectionTop.value)
        assertEquals(96f, regular.listBottom.value)
        assertEquals(44f, regular.calendarCell.value)
    }
}
