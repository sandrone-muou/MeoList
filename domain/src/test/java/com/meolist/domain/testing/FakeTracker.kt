package com.meolist.domain.testing

import com.meolist.domain.common.TransactionRunner
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.settings.AppSettings
import com.meolist.domain.task.ListFolder
import com.meolist.domain.task.TaskList
import com.meolist.domain.task.TaskListRepository
import com.meolist.domain.tracker.ActivityGoal
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.GoalAlarmPort
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.RunningTimerView
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TimeRecordRepository
import com.meolist.domain.tracker.TimerNotifier
import com.meolist.domain.tracker.ReachedGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePreferences(initial: AppSettings = AppSettings()) : AppPreferences {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }

    override val lastTaskFilter: Flow<String?> = MutableStateFlow(null)
    override suspend fun setLastTaskFilter(key: String) = Unit
}

/** Runs the block directly; tests are single threaded. */
object DirectTransactions : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

class FakeTaskListRepository : TaskListRepository {
    private val lists = MutableStateFlow<Map<Long, TaskList>>(emptyMap())
    private var nextId = 1L

    override fun observeLists(): Flow<List<TaskList>> = lists.map { it.values.toList() }
    override fun observeFolders(): Flow<List<ListFolder>> = MutableStateFlow(emptyList())
    override suspend fun getList(id: Long): TaskList? = lists.value[id]
    override suspend fun saveList(list: TaskList): Long {
        val id = if (list.id == 0L) nextId++ else list.id
        lists.value = lists.value + (id to list.copy(id = id))
        return id
    }

    override suspend fun deleteList(id: Long, at: Long) {
        lists.value = lists.value - id
    }

    override suspend fun saveFolder(folder: ListFolder): Long = 0
    override suspend fun deleteFolder(id: Long) = Unit
}

/** In-memory activity types and time records sharing one store, like the Room database. */
class FakeTracker : ActivityTypeRepository, TimeRecordRepository {
    val types = MutableStateFlow<Map<Long, ActivityType>>(emptyMap())
    val goals = MutableStateFlow<List<ActivityGoal>>(emptyList())
    private val reached = MutableStateFlow<Set<Triple<Long, GoalPeriod, Long>>>(emptySet())
    val running = MutableStateFlow<Map<Long, RunningTimer>>(emptyMap())
    val records = MutableStateFlow<Map<Long, TimeRecord>>(emptyMap())
    private var nextId = 1L

    // Activity types
    override fun observeTypes(): Flow<List<ActivityType>> = types.map { m -> m.values.sortedBy { it.sortOrder } }
    override suspend fun getTypes(): List<ActivityType> = types.value.values.sortedBy { it.sortOrder }
    override suspend fun getType(id: Long): ActivityType? = types.value[id]
    override suspend fun insertType(type: ActivityType): Long {
        val id = nextId++
        types.value = types.value + (id to type.copy(id = id))
        return id
    }

    override suspend fun updateType(type: ActivityType) {
        types.value = types.value + (type.id to type)
    }

    override suspend fun deleteType(id: Long) {
        types.value = types.value - id
        goals.value = goals.value.filter { it.typeId != id }
        running.value = running.value.filterValues { it.typeId != id }
        records.value = records.value.filterValues { it.typeId != id }
    }

    override suspend fun nextSortOrder(): Long = (types.value.values.maxOfOrNull { it.sortOrder } ?: 0) + 1
    override fun observeGoals(): Flow<List<ActivityGoal>> = goals
    override suspend fun getGoals(): List<ActivityGoal> = goals.value
    override suspend fun setGoals(typeId: Long, goals: List<ActivityGoal>) {
        this.goals.value = this.goals.value.filter { it.typeId != typeId } + goals
    }

    override suspend fun markGoalReached(typeId: Long, period: GoalPeriod, periodStartDay: Long, at: Long): Boolean {
        val key = Triple(typeId, period, periodStartDay)
        if (key in reached.value) return false
        reached.value = reached.value + key
        return true
    }

    override suspend fun getReachedGoals(): List<ReachedGoal> = reached.value.map {
        ReachedGoal(it.first, it.second, it.third, 0)
    }

    override fun observeReachedGoals(): Flow<List<ReachedGoal>> = reached.map { set ->
        set.map { ReachedGoal(it.first, it.second, it.third, 0) }
    }

    // Running timers
    override fun observeRunning(): Flow<List<RunningTimer>> = running.map { it.values.toList() }
    override suspend fun getRunning(): List<RunningTimer> = running.value.values.toList()
    override suspend fun getRunning(id: Long): RunningTimer? = running.value[id]
    override suspend fun getRunningByType(typeId: Long): RunningTimer? = running.value.values.firstOrNull { it.typeId == typeId }
    override suspend fun insertRunning(timer: RunningTimer): Long {
        check(running.value.values.none { it.typeId == timer.typeId }) { "typeId is unique" }
        val id = nextId++
        running.value = running.value + (id to timer.copy(id = id))
        return id
    }

    override suspend fun updateRunning(timer: RunningTimer) {
        running.value = running.value + (timer.id to timer)
    }

    override suspend fun deleteRunning(id: Long) {
        running.value = running.value - id
    }

    // Records
    override fun observeRecords(from: Long, to: Long): Flow<List<TimeRecord>> =
        records.map { m -> m.values.filter { it.startAt < to && it.endAt > from }.sortedByDescending { it.startAt } }

    override fun observeAllRecords(): Flow<List<TimeRecord>> =
        records.map { m -> m.values.sortedBy { it.startAt } }

    override suspend fun getAllRecords(): List<TimeRecord> = records.value.values.sortedBy { it.startAt }

    override suspend fun getRecords(from: Long, to: Long, typeId: Long?): List<TimeRecord> =
        records.value.values.filter { it.startAt < to && it.endAt > from && (typeId == null || it.typeId == typeId) }

    override suspend fun getRecord(id: Long): TimeRecord? = records.value[id]
    override suspend fun insertRecord(record: TimeRecord): Long {
        val id = nextId++
        records.value = records.value + (id to record.copy(id = id))
        return id
    }

    override suspend fun updateRecord(record: TimeRecord) {
        records.value = records.value + (record.id to record)
    }

    override suspend fun deleteRecord(id: Long) {
        records.value = records.value - id
    }

    override fun observeTaskTotalMs(taskId: Long): Flow<Long> =
        records.map { m -> m.values.filter { it.taskId == taskId }.sumOf { it.durationMs } }

    override suspend fun lastTypeForTask(taskId: Long): Long? =
        records.value.values.filter { it.taskId == taskId }.maxByOrNull { it.startAt }?.typeId
}

class FakeTimerNotifier : TimerNotifier {
    var shown: List<RunningTimerView> = emptyList()
    val goalsReached = mutableListOf<Pair<ActivityType, ActivityGoal>>()
    override fun showRunning(timers: List<RunningTimerView>) {
        shown = timers
    }

    override fun showGoalReached(type: ActivityType, goal: ActivityGoal) {
        goalsReached += type to goal
    }
}

class FakeGoalAlarmPort : GoalAlarmPort {
    var scheduledAt: Long? = null
    override fun schedule(atMillis: Long?) {
        scheduledAt = atMillis
    }
}
