package com.meolist.feature.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.tracker.ActivityGoal
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TimeRecordRepository
import com.meolist.domain.tracker.TimerInteractor
import com.meolist.domain.tracker.TrackerTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A goal period of the current date as [from, to) millis. */
data class PeriodWindow(val period: GoalPeriod, val from: Long, val to: Long)

data class TypeCard(
    val type: ActivityType,
    val running: RunningTimer?,
    val runningTaskTitle: String?,
    /** Recorded (finished) time per period window, running time excluded. */
    val recordedMs: Map<GoalPeriod, Long>,
    /** The goal shown on the card: the shortest period that has one. */
    val goal: ActivityGoal?,
)

data class TrackerState(
    val loaded: Boolean = false,
    val cards: List<TypeCard> = emptyList(),
    val archived: List<ActivityType> = emptyList(),
    val windows: Map<GoalPeriod, PeriodWindow> = emptyMap(),
    val runningCount: Int = 0,
)

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val types: ActivityTypeRepository,
    private val records: TimeRecordRepository,
    private val tasks: TaskRepository,
    private val timers: TimerInteractor,
    prefs: AppPreferences,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    fun now(): Long = clock.now()

    /** Today, re-checked every minute so totals roll over at midnight. */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(clock.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val windows: Flow<Map<GoalPeriod, PeriodWindow>> =
        combine(today, prefs.settings.map { it.firstDayOfWeek }.distinctUntilChanged()) { day, firstDay ->
            windowsFor(day, firstDay)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val periodRecords: Flow<Pair<Map<GoalPeriod, PeriodWindow>, List<TimeRecord>>> = windows.flatMapLatest { w ->
        val from = w.values.minOf { it.from }
        val to = w.values.maxOf { it.to }
        records.observeRecords(from, to).map { w to it }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val runningWithTitles: Flow<Pair<List<RunningTimer>, Map<Long, String>>> =
        records.observeRunning().mapLatest { running ->
            val titles = running.mapNotNull { it.taskId }.distinct()
                .mapNotNull { id -> tasks.get(id)?.let { id to it.title } }
                .toMap()
            running to titles
        }

    val state: StateFlow<TrackerState> = combine(
        types.observeTypes(),
        types.observeGoals(),
        runningWithTitles,
        periodRecords,
    ) { allTypes, goals, (running, titles), (w, recs) ->
        val goalsByType = goals.groupBy { it.typeId }
        val runningByType = running.associateBy { it.typeId }
        val recsByType = recs.groupBy { it.typeId }
        val cards = allTypes.filter { !it.archived }.map { type ->
            val own = recsByType[type.id].orEmpty()
            val timer = runningByType[type.id]
            TypeCard(
                type = type,
                running = timer,
                runningTaskTitle = timer?.taskId?.let { titles[it] },
                recordedMs = w.mapValues { (_, win) -> own.sumOf { TrackerTime.overlapMs(it.startAt, it.endAt, win.from, win.to) } },
                goal = goalsByType[type.id].orEmpty().minByOrNull { it.period.value },
            )
        }
        TrackerState(
            loaded = true,
            cards = cards,
            archived = allTypes.filter { it.archived },
            windows = w,
            runningCount = running.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackerState())

    fun toggle(card: TypeCard) {
        appScope.launch {
            if (card.running != null) timers.stop(card.running.id) else timers.start(card.type.id)
        }
    }

    fun stopAll() = appScope.launch { timers.stopAll() }

    fun discard(runningId: Long) = appScope.launch { timers.discard(runningId) }

    fun updateRunning(timer: RunningTimer, startedAt: Long, comment: String) = appScope.launch {
        timers.updateRunning(timer.id, startedAt, comment, timer.taskId)
    }

    fun setArchived(typeId: Long, archived: Boolean) = appScope.launch { timers.setArchived(typeId, archived) }

    /** Creates a starter set of activities so a new user has something to tap. */
    fun addSuggested(suggestions: List<ActivityType>) = appScope.launch {
        suggestions.forEach { timers.saveType(it, emptyList()) }
    }

    private fun windowsFor(day: LocalDate, firstDay: DayOfWeek): Map<GoalPeriod, PeriodWindow> =
        GoalPeriod.entries.associateWith { period ->
            val (from, to) = TrackerTime.periodRange(period, day, firstDay, clock.zone())
            PeriodWindow(period, from, to)
        }
}
