package com.meolist.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

fun Color.readableOn(background: Color = Color.White): Color {
    val dark = Color(0xFF17212B)
    val luminance = compositeOver(background).luminance()
    val darkLuminance = dark.luminance()
    val darkContrast = (maxOf(luminance, darkLuminance) + 0.05f) / (minOf(luminance, darkLuminance) + 0.05f)
    val lightContrast = 1.05f / (luminance + 0.05f)
    return if (darkContrast >= lightContrast) dark else Color.White
}
