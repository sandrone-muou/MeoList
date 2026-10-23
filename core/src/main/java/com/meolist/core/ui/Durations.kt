package com.meolist.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.meolist.core.R
import kotlinx.coroutines.delay

/** Stopwatch text: "12:34" below an hour, "1:02:03" above. Negative values show as zero. */
fun elapsedText(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Readable length such as "1h 20m" / "1小时20分钟", rounded down to whole minutes. */
@Composable
@ReadOnlyComposable
fun durationText(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return when {
        h == 0 -> stringResource(R.string.core_duration_m, m)
        m == 0 -> stringResource(R.string.core_duration_h, h)
        else -> stringResource(R.string.core_duration_hm, h, m)
    }
}

/**
 * The current time, refreshed every [intervalMs] while the screen is started. Stops ticking in the
 * background so idle screens cost nothing.
 */
@Composable
fun rememberTicker(now: () -> Long, intervalMs: Long = 1000): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initialValue = now(), lifecycle, intervalMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = now()
                // Align to the next whole interval so seconds flip together.
                delay(intervalMs - value % intervalMs)
            }
        }
    }
}
