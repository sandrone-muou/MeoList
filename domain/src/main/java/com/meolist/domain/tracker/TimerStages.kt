package com.meolist.domain.tracker

data class ActivitySubproject(
    val id: Long = 0,
    val typeId: Long,
    val name: String,
    val color: Int,
    val sortOrder: Long = 0,
    val archived: Boolean = false,
)

/** Assignment snapshots survive renames and archiving. Future stages have no timestamps. */
data class TimerStage(
    val position: Int,
    val subprojectId: Long? = null,
    val name: String = "",
    val color: Int = 0xFF78909C.toInt(),
    val startAt: Long? = null,
    val endAt: Long? = null,
) {
    val durationMs: Long get() = if (startAt != null && endAt != null) (endAt - startAt).coerceAtLeast(0) else 0
    fun assignedTo(project: ActivitySubproject?) = copy(
        subprojectId = project?.id, name = project?.name.orEmpty(), color = project?.color ?: 0xFF78909C.toInt(),
    )
}

object TimerStages {
    fun current(timer: RunningTimer): TimerStage = timer.stages.firstOrNull { it.startAt != null && it.endAt == null }
        ?: TimerStage(1, startAt = timer.startedAt)

    fun finish(timer: RunningTimer, endAt: Long): List<TimerStage> {
        if (timer.stages.isEmpty()) return emptyList()
        return timer.stages.filter { it.startAt != null }.map {
            if (it.endAt == null) it.copy(endAt = maxOf(endAt, requireNotNull(it.startAt))) else it
        }.filter { it.durationMs > 0 }
    }

    /** Keep real boundaries when editing; newly exposed time is explicitly unassigned. */
    fun resized(stages: List<TimerStage>, start: Long, end: Long): List<TimerStage> {
        if (stages.isEmpty()) return emptyList()
        val result = mutableListOf<TimerStage>()
        var cursor = start
        stages.sortedBy { it.position }.forEach { stage ->
            val s = maxOf(stage.startAt ?: return@forEach, start, cursor)
            val e = minOf(stage.endAt ?: return@forEach, end)
            if (e > s) {
                if (s > cursor) result += TimerStage(0, startAt = cursor, endAt = s)
                result += stage.copy(startAt = s, endAt = e)
                cursor = e
            }
        }
        if (cursor < end) result += TimerStage(0, startAt = cursor, endAt = end)
        return result.mapIndexed { index, stage -> stage.copy(position = index + 1) }
    }
}
