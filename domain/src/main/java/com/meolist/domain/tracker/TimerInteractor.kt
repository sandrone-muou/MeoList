package com.meolist.domain.tracker

import com.meolist.domain.common.Clock
import com.meolist.domain.common.TransactionRunner
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.TaskListRepository
import com.meolist.domain.task.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Write operations for activity timing. A running timer only stores its start; stopping turns it into
 * a [TimeRecord] in one transaction. Every change ends with [TimerSync] so notifications and goal
 * alarms follow.
 */
@Singleton
class TimerInteractor @Inject constructor(
    private val records: TimeRecordRepository,
    private val types: ActivityTypeRepository,
    private val tasks: TaskRepository,
    private val lists: TaskListRepository,
    private val prefs: AppPreferences,
    private val tx: TransactionRunner,
    private val sync: TimerSync,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /**
     * Starts a timer of [typeId], optionally linked to [taskId]. A timer of the same type that tracks
     * the same task keeps running; one tracking something else is stopped and recorded first. With
     * parallel timers turned off every other timer is stopped too. Returns the running timer's id.
     */
    suspend fun start(typeId: Long, taskId: Long? = null): Long = mutex.withLock {
        val settings = prefs.settings.first()
        val now = clock.now()
        val id = tx.run {
            val existing = records.getRunningByType(typeId)
            if (existing != null && existing.taskId == taskId) {
                existing.id
            } else {
                val toStop = if (settings.multipleTimers) listOfNotNull(existing) else records.getRunning()
                toStop.forEach { finish(it, now, settings.ignoreShortRecordSec) }
                records.insertRunning(RunningTimer(typeId = typeId, taskId = taskId, startedAt = now))
            }
        }
        sync.sync()
        id
    }

    /** Stops a running timer and returns the new record's id, or null when it was too short to keep. */
    suspend fun stop(runningId: Long): Long? = mutex.withLock {
        val timer = records.getRunning(runningId) ?: return@withLock null
        val settings = prefs.settings.first()
        val recordId = tx.run { finish(timer, clock.now(), settings.ignoreShortRecordSec) }
        sync.sync()
        recordId
    }

    suspend fun stopAll() = stopWhere { true }

    /** Stops the timers linked to a task, e.g. when it is completed. */
    suspend fun stopForTask(taskId: Long) = stopWhere { it.taskId == taskId }

    /** Throws a running timer away without recording it. */
    suspend fun discard(runningId: Long) = mutex.withLock {
        records.deleteRunning(runningId)
        sync.sync()
    }

    /** Adjusts a running timer; the start can not move into the future. */
    suspend fun updateRunning(runningId: Long, startedAt: Long, comment: String, taskId: Long?) = mutex.withLock {
        val timer = records.getRunning(runningId) ?: return@withLock
        records.updateRunning(timer.copy(startedAt = minOf(startedAt, clock.now()), comment = comment.trim(), taskId = taskId))
        sync.sync()
    }

    /**
     * Adds a record entered by hand ([TimeRecord.id] 0) or updates an existing one. A new record is
     * marked manual; an edited one keeps how it was created. Nothing is written when the record is
     * invalid.
     */
    suspend fun saveRecord(record: TimeRecord): RecordSaveResult {
        val type = types.getType(record.typeId)
        RecordRules.validate(record, typeExists = type != null, now = clock.now())?.let { return RecordSaveResult.Invalid(it) }
        val clean = record.copy(comment = record.comment.trim(), taskId = record.taskId?.takeIf { tasks.get(it) != null })
        val existing = if (clean.id != 0L) records.getRecord(clean.id) else null
        val id = if (existing == null) {
            records.insertRecord(clean.copy(id = 0, isManual = true))
        } else {
            records.updateRecord(clean.copy(isManual = existing.isManual))
            existing.id
        }
        sync.sync()
        return RecordSaveResult.Saved(id)
    }

    /** Deletes a record and returns it, so the deletion can be undone with [restoreRecord]. */
    suspend fun deleteRecord(id: Long): TimeRecord? {
        val record = records.getRecord(id) ?: return null
        records.deleteRecord(id)
        sync.sync()
        return record
    }

    suspend fun restoreRecord(record: TimeRecord) {
        if (types.getType(record.typeId) == null) return
        records.insertRecord(record.copy(id = 0, taskId = record.taskId?.takeIf { tasks.get(it) != null }))
        sync.sync()
    }

    /**
     * The activity type a timer for [taskId] should use without asking: the list's default type, or the
     * type last tracked for the task. Archived or deleted types are skipped.
     */
    suspend fun defaultTypeFor(taskId: Long): Long? {
        val task = tasks.get(taskId) ?: return null
        val listDefault = task.listId?.let { lists.getList(it) }?.defaultActivityTypeId
        return listOfNotNull(listDefault, records.lastTypeForTask(taskId))
            .firstOrNull { id -> types.getType(id)?.archived == false }
    }

    /** Makes [typeId] the default activity of a list (null clears it). */
    suspend fun setListDefaultType(listId: Long, typeId: Long?) {
        val list = lists.getList(listId) ?: return
        if (list.defaultActivityTypeId != typeId) lists.saveList(list.copy(defaultActivityTypeId = typeId))
    }

    /** Creates or updates an activity type together with its goals (one per period at most). */
    suspend fun saveType(type: ActivityType, goals: List<ActivityGoal>): Long {
        val clean = type.copy(name = type.name.trim())
        require(clean.name.isNotEmpty()) { "name must not be blank" }
        val id = tx.run {
            val id = if (clean.id == 0L) types.insertType(clean.copy(sortOrder = types.nextSortOrder())) else {
                types.updateType(clean)
                clean.id
            }
            types.setGoals(id, goals.filter { it.targetSec > 0 }.distinctBy { it.period }.map { it.copy(typeId = id) })
            id
        }
        afterTypeChange(id, clean.archived)
        return id
    }

    suspend fun setArchived(typeId: Long, archived: Boolean) {
        val type = types.getType(typeId) ?: return
        types.updateType(type.copy(archived = archived))
        afterTypeChange(typeId, archived)
    }

    /** Archiving a type stops its timer; either way notifications and goals are refreshed. */
    private suspend fun afterTypeChange(typeId: Long, archived: Boolean) {
        val running = if (archived) records.getRunningByType(typeId) else null
        if (running != null) stop(running.id) else sync.sync()
    }

    /** Deletes a type with all of its records. */
    suspend fun deleteType(typeId: Long) = mutex.withLock {
        types.deleteType(typeId)
        sync.sync()
    }

    private suspend fun stopWhere(predicate: (RunningTimer) -> Boolean) = mutex.withLock {
        val settings = prefs.settings.first()
        val now = clock.now()
        val stopped = tx.run {
            records.getRunning().filter(predicate).onEach { finish(it, now, settings.ignoreShortRecordSec) }
        }
        if (stopped.isNotEmpty()) sync.sync()
    }

    /** Removes the running timer and records it unless it is empty or shorter than [ignoreShortSec]. */
    private suspend fun finish(timer: RunningTimer, endAt: Long, ignoreShortSec: Int): Long? {
        records.deleteRunning(timer.id)
        // A clock moved backwards would give a negative duration, which counts as zero.
        val end = maxOf(endAt, timer.startedAt)
        if (end - timer.startedAt < maxOf(ignoreShortSec * 1000L, 1L)) return null
        return records.insertRecord(
            TimeRecord(typeId = timer.typeId, taskId = timer.taskId, startAt = timer.startedAt, endAt = end, comment = timer.comment),
        )
    }
}
