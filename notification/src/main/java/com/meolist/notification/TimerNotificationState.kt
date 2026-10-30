package com.meolist.notification

import android.util.AtomicFile
import android.util.Log
import com.meolist.domain.tracker.RunningTimerView
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class TimerNotificationState(file: File) {
    private val storage = AtomicFile(file)
    private var dismissed = read()
    private var dirty = false

    @Synchronized
    fun visible(timers: List<RunningTimerView>): List<RunningTimerView> {
        val activeIds = timers.mapTo(mutableSetOf()) { it.timer.id }
        val activeDismissals = dismissed.intersect(activeIds)
        if (activeDismissals.size != dismissed.size) {
            dismissed = activeDismissals
            dirty = true
        }
        return visibleTimers(timers, dismissed)
    }

    @Synchronized
    fun mark(ids: Set<Long>) {
        val next = dismissed + ids
        if (next.size == dismissed.size) return
        dismissed = next
        dirty = true
    }

    @Synchronized
    fun persist() {
        if (!dirty) return
        var stream: java.io.FileOutputStream? = null
        try {
            stream = storage.startWrite()
            stream.write(dismissed.joinToString("\n").toByteArray(Charsets.UTF_8))
            storage.finishWrite(stream)
            dirty = false
        } catch (e: IOException) {
            storage.failWrite(stream)
            Log.w("TimerNotificationState", "Cannot persist dismissed timers", e)
        }
    }

    private fun read(): Set<Long> = try {
        storage.openRead().bufferedReader().useLines { lines ->
            lines.mapNotNull(String::toLongOrNull).toSet()
        }
    } catch (_: FileNotFoundException) {
        emptySet()
    } catch (e: IOException) {
        Log.w("TimerNotificationState", "Cannot read dismissed timers", e)
        emptySet()
    }
}

internal fun visibleTimers(timers: List<RunningTimerView>, dismissed: Set<Long>): List<RunningTimerView> =
    timers.filterNot { it.timer.id in dismissed }
        .sortedWith(compareBy({ it.timer.startedAt }, { it.timer.id }))
