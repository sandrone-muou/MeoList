package com.meolist.notification

import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.RunningTimerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerNotificationSelectionTest {
    @Test fun oldestVisibleTimerIsSelectedRegardlessOfInputOrder() {
        val timers = listOf(timer(3, 200), timer(2, 100), timer(1, 100))
        assertEquals(listOf(1L, 2L, 3L), visibleTimers(timers, emptySet()).map { it.timer.id })
    }

    @Test fun dismissedTimerIsSkippedAndNextTimerTakesOver() {
        val timers = listOf(timer(1, 100), timer(2, 200))
        assertEquals(listOf(2L), visibleTimers(timers, setOf(1)).map { it.timer.id })
        assertEquals(listOf(2L), visibleTimers(timers.drop(1), setOf(1)).map { it.timer.id })
    }

    @Test fun dismissalDoesNotChangeTimersOrSuppressNewSessions() {
        val timers = listOf(timer(1, 100), timer(2, 200))
        assertTrue(visibleTimers(timers, setOf(1, 2)).isEmpty())
        assertEquals(2, timers.size)
        assertEquals(3L, visibleTimers(listOf(timer(3, 300)), setOf(1, 2)).single().timer.id)
    }

    @Test fun noTimersProducesNoSelection() {
        assertTrue(visibleTimers(emptyList(), setOf(1)).isEmpty())
    }

    private fun timer(id: Long, start: Long) = RunningTimerView(
        RunningTimer(id = id, typeId = id, startedAt = start),
        ActivityType(id = id, name = "Activity $id", icon = "timer", color = 0xFF4A6382.toInt()),
        null,
    )
}
