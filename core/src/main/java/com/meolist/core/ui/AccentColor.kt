package com.meolist.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * User-picked colours come from older data too; a fully transparent value would make icons and
 * buttons disappear, so fall back to the theme's primary.
 */
@Composable
fun accentColor(argb: Int, fallback: Color = MaterialTheme.colorScheme.primary): Color {
    val color = Color(argb)
    return if (color.alpha < 0.2f) fallback else color
}
