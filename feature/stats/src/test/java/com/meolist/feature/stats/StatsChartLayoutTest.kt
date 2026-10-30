package com.meolist.feature.stats

import com.meolist.core.ui.charts.donutCenterSide
import com.meolist.core.ui.charts.donutDiameterForCenter
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsChartLayoutTest {
    @Test
    fun centerCornersStayInsideSelectedStrokeWithPadding() {
        listOf(120f, 144f, 180f, 240f, 320f).forEach { diameter ->
            listOf(20f, 28f).forEach { thickness ->
                val side = donutCenterSide(diameter, thickness, 6f)
                val cornerRadius = hypot(side / 2f, side / 2f)
                val innerRadius = diameter / 2f - thickness * 1.25f
                assertTrue(cornerRadius + 6f <= innerRadius + 0.001f)
                assertTrue(side < innerRadius * 2f)
            }
        }
    }

    @Test
    fun requiredDiameterReservesBothWidthAndHeightOfMeasuredContent() {
        listOf(40f to 56f, 80f to 56f, 105f to 82f, 70f to 116f).forEach { (width, height) ->
            val needed = maxOf(width, height) + 2f
            val diameter = donutDiameterForCenter(needed, 20f, 6f)
            val available = donutCenterSide(diameter, 20f, 6f)
            assertTrue(available >= width)
            assertTrue(available >= height)
            assertEquals(needed, available, 0.001f)
        }
    }

    @Test
    fun collapsedHoleHasNoNegativeCenterSize() {
        assertEquals(0f, donutCenterSide(0f, 20f), 0f)
        assertEquals(0f, donutCenterSide(40f, 20f), 0f)
    }

    @Test
    fun englishAndChineseDurationGroupsKeepAllHoursAndMinutes() {
        val cases = listOf(
            0L to listOf("0m"),
            59L to listOf("59m"),
            60L to listOf("1h"),
            61L to listOf("1h", "1m"),
            119L to listOf("1h", "59m"),
            99L * 60 to listOf("99h"),
            99L * 60 + 59 to listOf("99h", "59m"),
            100L * 60 to listOf("100h"),
            100L * 60 + 59 to listOf("100h", "59m"),
            1000L * 60 to listOf("1000h"),
            1000L * 60 + 59 to listOf("1000h", "59m"),
        )
        cases.forEach { (minutes, expected) ->
            assertEquals(expected, statsDurationGroups(minutes * 60_000, "h", "m"))
            assertEquals(
                expected.map { it.replace("h", "小时").replace("m", "分钟") },
                statsDurationGroups(minutes * 60_000, "小时", "分钟"),
            )
        }
        assertEquals(listOf("0m"), statsDurationGroups(-1, "h", "m"))
    }

    @Test
    fun summaryMetricsKeepThreeColumnsWhenWideEnough() {
        assertEquals(3, summaryColumns(349f, 1f))
        assertEquals(3, summaryColumns(300f, 1f))
        assertEquals(2, summaryColumns(299f, 1f))
        assertEquals(2, summaryColumns(264f, 1f))
    }

    @Test
    fun summaryMetricsDropToTwoColumnsAtLargeFonts() {
        assertEquals(2, summaryColumns(349f, 1.3f))
        assertEquals(2, summaryColumns(389f, 1.3f))
        assertEquals(3, summaryColumns(390f, 1.3f))
        assertEquals(3, summaryColumns(600f, 1.3f))
    }
}
