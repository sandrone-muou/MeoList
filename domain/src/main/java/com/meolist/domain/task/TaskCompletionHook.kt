package com.meolist.domain.task

/** Follow-up work when a task is completed or reopened (timers now, farm nutrients later). */
fun interface TaskCompletionHook {
    suspend fun onDoneChanged(taskId: Long, done: Boolean)

    companion object {
        val None = TaskCompletionHook { _, _ -> }
    }
}
