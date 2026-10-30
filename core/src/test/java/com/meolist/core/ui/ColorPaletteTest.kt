package com.meolist.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPaletteTest {
    @Test
    fun sharedPaletteKeepsItsOriginalOrderAndDefault() {
        val original = listOf(
            0xFF3B6939, 0xFF6A9F4B, 0xFF2E7D6B, 0xFF1E88A8, 0xFF3F51B5,
            0xFF7E57C2, 0xFFC2185B, 0xFFE53935, 0xFFF4511E, 0xFFFB8C00,
            0xFFF9A825, 0xFF8D6E63, 0xFF546E7A, 0xFF9E9E9E,
        ).map { it.toInt() }
        assertEquals(original, MeoPalette.colors)
        assertEquals(original.first(), MeoPalette.default)
    }

    @Test
    fun newActivitiesStartWithBlueGrayWithoutChangingSharedChoices() {
        assertEquals(0xFF718A9E.toInt(), ActivityPalette.default)
        assertEquals(ActivityPalette.colors.first(), ActivityPalette.default)
        assertEquals(ActivityPalette.colors.distinct(), ActivityPalette.colors)
    }

    @Test
    fun selectedPaletteColorIsNotDuplicatedOrReordered() {
        assertEquals(MeoPalette.colors, pickerColors(MeoPalette.colors, MeoPalette.colors[6]))
        assertEquals(ActivityPalette.colors, pickerColors(ActivityPalette.colors, ActivityPalette.default))
    }

    @Test
    fun historicalArgbIncludingAlphaIsAppendedUnchanged() {
        listOf(0xFF123456.toInt(), 0x40718A9E, 0).forEach { historical ->
            val result = pickerColors(ActivityPalette.colors, historical)
            assertEquals(ActivityPalette.colors + historical, result)
            assertEquals(historical, result.last())
        }
    }

    @Test
    fun originalHistoricalColorRemainsAvailableAfterSelectingARecommendation() {
        val historical = 0xFFE53935.toInt()
        val choices = ActivityPalette.colors + historical
        val result = pickerColors(choices, ActivityPalette.default)
        assertEquals(choices, result)
        assertTrue(historical in result)
        assertEquals(1, result.count { it == ActivityPalette.default })
    }

    @Test
    fun emptyChoicesStillShowTheSelectedArgb() {
        assertEquals(listOf(0x40123456), pickerColors(emptyList(), 0x40123456))
    }
}
