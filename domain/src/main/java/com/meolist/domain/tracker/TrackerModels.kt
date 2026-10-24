package com.meolist.domain.tracker

import kotlinx.coroutines.flow.Flow

/** Something time is tracked for, such as "Work" or "Reading". [icon] is a key resolved by the UI. */
data class ActivityType(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    val sortOrder: Long = 0,
    val archived: Boolean = false,
)

enum class GoalPeriod(val value: Int) {
    DAY(0), WEEK(1), MONTH(2);

    companion object {
        fun fromValue(value: Int): GoalPeriod = entries.firstOrNull { it.value == value } ?: DAY
    }
}

/** Track at least [targetSec] of an activity type per [period]. */
data class ActivityGoal(
    val typeId: Long,
    val period: GoalPeriod,
    val targetSec: Long,
)

/** A timer that is running right now. Only the start is stored, so it survives process death. */
data class RunningTimer(
    val id: Long = 0,
    val typeId: Long,
    val taskId: Long? = null,
    val startedAt: Long,
    val comment: String = "",
)

/** A finished stretch of tracked time. */
data class TimeRecord(
    val id: Long = 0,
    val typeId: Long,
    val taskId: Long? = null,
    val startAt: Long,
    val endAt: Long,
    val comment: String = "",
    val isManual: Boolean = false,
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0)
}

/** A running timer with what a notification needs to describe it. */
data class RunningTimerView(
    val timer: RunningTimer,
    val type: ActivityType,
    val taskTitle: String?,
)

interface ActivityTypeRepository {
    /** Every type, archived ones included, in display order. */
    fun observeTypes(): Flow<List<ActivityType>>
    suspend fun getTypes(): List<ActivityType>
    suspend fun getType(id: Long): ActivityType?
    suspend fun insertType(type: ActivityType): Long
    suspend fun updateType(type: ActivityType)
    /** Deletes the type together with its records, goals and running timer. */
    suspend fun deleteType(id: Long)
    suspend fun nextSortOrder(): Long

    fun observeGoals(): Flow<List<ActivityGoal>>
    suspend fun getGoals(): List<ActivityGoal>
    suspend fun setGoals(typeId: Long, goals: List<ActivityGoal>)

    /**
     * Remembers that the goal of [typeId] for [period] was reached in the period starting on
     * [periodStartDay] (epoch day). Returns false when that was already recorded.
     */
    suspend fun markGoalReached(typeId: Long, period: GoalPeriod, periodStartDay: Long, at: Long): Boolean

    /** Every goal period ever reached, for the farm's nutrient ledger. */
    suspend fun getReachedGoals(): List<ReachedGoal>
    fun observeReachedGoals(): Flow<List<ReachedGoal>>
}

/** One goal period that was reached once. */
data class ReachedGoal(
    val typeId: Long,
    val period: GoalPeriod,
    val periodStartDay: Long,
    val reachedAt: Long,
)

interface TimeRecordRepository {
    fun observeRunning(): Flow<List<RunningTimer>>
    suspend fun getRunning(): List<RunningTimer>
    suspend fun getRunning(id: Long): RunningTimer?
    suspend fun getRunningByType(typeId: Long): RunningTimer?
    suspend fun insertRunning(timer: RunningTimer): Long
    suspend fun updateRunning(timer: RunningTimer)
    suspend fun deleteRunning(id: Long)

    /** Records overlapping [from, to), newest first. */
    fun observeRecords(from: Long, to: Long): Flow<List<TimeRecord>>
    /** Every record, for totals and the farm. */
    fun observeAllRecords(): Flow<List<TimeRecord>>
    suspend fun getAllRecords(): List<TimeRecord>
    /** Records overlapping [from, to), optionally of one type only. */
    suspend fun getRecords(from: Long, to: Long, typeId: Long? = null): List<TimeRecord>
    suspend fun getRecord(id: Long): TimeRecord?
    suspend fun insertRecord(record: TimeRecord): Long
    suspend fun updateRecord(record: TimeRecord)
    suspend fun deleteRecord(id: Long)

    /** Total recorded milliseconds linked to a task (running timers excluded). */
    fun observeTaskTotalMs(taskId: Long): Flow<Long>
    /** Type of the latest record linked to the task. */
    suspend fun lastTypeForTask(taskId: Long): Long?
}

/** Shows running timers as ongoing notifications and announces reached goals. */
interface TimerNotifier {
    /** Makes the visible timer notifications match [timers] exactly. */
    fun showRunning(timers: List<RunningTimerView>)
    fun showGoalReached(type: ActivityType, goal: ActivityGoal)
}

/** Wakes the app when the next running goal will be reached. */
interface GoalAlarmPort {
    /** Replaces the goal alarm; null cancels it. */
    fun schedule(atMillis: Long?)
}
