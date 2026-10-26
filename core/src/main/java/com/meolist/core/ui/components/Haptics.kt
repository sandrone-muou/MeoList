package com.meolist.core.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Haptic that maps Compose's small set to Android's richer constants on newer devices.
 * Falls back to a virtual key press on API 26–29.
 */
@Composable
fun rememberConfirmHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(type)
        }
    }
}

/** For destructive or rejected actions. */
@Composable
fun rememberRejectHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(type)
        }
    }
}
