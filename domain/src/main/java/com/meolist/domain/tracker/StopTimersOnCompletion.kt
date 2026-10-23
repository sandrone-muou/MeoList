package com.meolist.domain.tracker

import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.TaskCompletionHook
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Stops a task's timers when it is completed, if the user wants that. */
class StopTimersOnCompletion @Inject constructor(
    private val timers: TimerInteractor,
    private val prefs: AppPreferences,
) : TaskCompletionHook {
    override suspend fun onDoneChanged(taskId: Long, done: Boolean) {
        if (done && prefs.settings.first().stopTimerOnTaskComplete) timers.stopForTask(taskId)
    }
}
