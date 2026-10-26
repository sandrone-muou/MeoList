package com.meolist.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Dark or light content, whichever stays readable on [this] background. */
fun Color.readableOn(): Color = if (luminance() > 0.55f) Color(0xFF17201A) else Color.White
