package com.meolist.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {
    private val dark = Color(0xFF17212B)

    @Test
    fun darkBackgroundsUseWhiteAndLightBackgroundsUseNeutralBlueGray() {
        assertEquals(Color.White, Color.Black.readableOn())
        assertEquals(Color.White, Color(0xFF3F51B5).readableOn())
        assertEquals(dark, Color.White.readableOn())
        assertEquals(dark, Color(0xFF9E9E9E).readableOn())
    }

    @Test
    fun bothPalettesChooseTheHigherContrastForeground() {
        (MeoPalette.colors + ActivityPalette.colors).forEach { argb ->
            val background = Color(argb)
            val chosenContrast = contrast(background, background.readableOn())
            assertEquals(
                maxOf(contrast(background, dark), contrast(background, Color.White)),
                chosenContrast,
                0.0001f,
            )
            assertTrue(chosenContrast >= 3f)
        }
    }

    @Test
    fun translucentHistoricalColorsUseTheCompositedSurface() {
        val historical = Color(0x40718A9E)
        listOf(Color.White, dark).forEach { surface ->
            val effective = historical.compositeOver(surface)
            assertEquals(effective.readableOn(), historical.readableOn(surface))
        }
        assertEquals(dark, Color.Transparent.readableOn(Color.White))
        assertEquals(Color.White, Color.Transparent.readableOn(dark))
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
