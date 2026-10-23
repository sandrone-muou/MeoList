package com.meolist.domain.tracker

import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Brings everything outside the database in line with the timers: the ongoing notifications,
 * reached-goal announcements and the alarm for the next goal. Call after any timer or record change,
 * on app start and after boot.
 */
@Singleton
class TimerSync @Inject constructor(
    private val records: TimeRecordRepository,
    private val types: ActivityTypeRepository,
    private val tasks: TaskRepository,
    private val prefs: AppPreferences,
    private val notifier: TimerNotifier,
    private val alarm: GoalAlarmPort,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun sync() = mutex.withLock {
        val now = clock.now()
        val running = records.getRunning()
        val typeMap = types.getTypes().associateBy { it.id }
        val views = running.sortedBy { it.startedAt }.mapNotNull { timer ->
            typeMap[timer.typeId]?.let { RunningTimerView(timer, it, timer.taskId?.let { id -> tasks.get(id)?.title }) }
        }
        notifier.showRunning(views)
        alarm.schedule(checkGoals(running, typeMap, now))
    }

    /** Announces goals reached by now and returns when the next one will be reached by a running timer. */
    private suspend fun checkGoals(running: List<RunningTimer>, typeMap: Map<Long, ActivityType>, now: Long): Long? {
        val goals = types.getGoals()
        if (goals.isEmpty()) return null
        val settings = prefs.settings.first()
        val zone = clock.zone()
        val today = clock.today()
        var next: Long? = null
        for (goal in goals) {
            val type = typeMap[goal.typeId]?.takeIf { !it.archived } ?: continue
            val start = TrackerTime.periodStart(goal.period, today, settings.firstDayOfWeek)
            val (from, to) = TrackerTime.periodRange(goal.period, today, settings.firstDayOfWeek, zone)
            val ownRunning = running.filter { it.typeId == goal.typeId }
            val done = TrackerTime.totalMs(records.getRecords(from, to, goal.typeId), ownRunning, from, to, now)
            val target = goal.targetSec * 1000
            if (done >= target) {
                if (types.markGoalReached(goal.typeId, goal.period, start.toEpochDay(), now)) {
                    notifier.showGoalReached(type, goal)
                }
            } else if (ownRunning.isNotEmpty()) {
                // A new period starts from zero, so check again at its start at the latest.
                val at = minOf(now + (target - done), to)
                next = next?.let { minOf(it, at) } ?: at
            }
        }
        return next
    }
}
