package com.meolist.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meolist.domain.calendar.CalendarPeriod
import com.meolist.domain.calendar.TaskCalendarRules
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.reminder.ReminderRepository
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.ListFolder
import com.meolist.domain.task.RepeatAdvance
import com.meolist.domain.task.Tag
import com.meolist.domain.task.TagRepository
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskCreator
import com.meolist.domain.task.TaskDraft
import com.meolist.domain.task.TaskFilter
import com.meolist.domain.task.TaskGroup
import com.meolist.domain.task.TaskGrouping
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.task.TaskItem
import com.meolist.domain.task.TaskList
import com.meolist.domain.task.TaskListRepository
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.task.TaskSnapshot
import com.meolist.domain.tracker.TimeRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskView { LIST, CALENDAR }

private data class TaskPresentation(
    val view: TaskView,
    val period: CalendarPeriod,
    val selected: LocalDate,
)

private data class PresentedSnapshot(
    val snapshot: TaskSnapshot,
    val presentation: TaskPresentation,
    val firstDayOfWeek: DayOfWeek,
)

data class TasksUiState(
    val loading: Boolean = true,
    val view: TaskView = TaskView.LIST,
    val calendarPeriod: CalendarPeriod = CalendarPeriod.MONTH,
    val selectedDate: LocalDate = LocalDate.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val calendarTasks: Map<LocalDate, List<TaskItem>> = emptyMap(),
    val filter: TaskFilter = TaskFilter.Today,
    val groups: List<TaskGroup> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val folders: List<ListFolder> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** Open task counts keyed by [TaskFilter.key]. */
    val counts: Map<String, Int> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Tasks with at least one enabled reminder. */
    val reminderTaskIds: Set<Long> = emptySet(),
    /** Tasks with a running timer. */
    val timingTaskIds: Set<Long> = emptySet(),
) {
    val isTrash: Boolean get() = filter == TaskFilter.Trash
}

private data class Extras(
    val trash: List<Task>,
    val folders: List<ListFolder>,
    val reminderIds: Set<Long>,
    val timingIds: Set<Long>,
)

/** Defaults for the quick-add sheet derived from the current filter. */
data class QuickAddDefaults(val date: LocalDate?, val listId: Long?, val tagIds: Set<Long>)

@HiltViewModel
class TasksViewModel @Inject constructor(
    taskRepo: TaskRepository,
    listRepo: TaskListRepository,
    tagRepo: TagRepository,
    private val interactor: TaskInteractor,
    private val prefs: AppPreferences,
    reminderRepo: ReminderRepository,
    timeRecords: TimeRecordRepository,
    private val clock: Clock,
    private val creator: TaskCreator,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val filter = MutableStateFlow<TaskFilter?>(null)
    private val presentation = MutableStateFlow(TaskPresentation(
        view = savedState.get<String>("taskView")?.let { TaskView.valueOf(it) } ?: TaskView.LIST,
        period = savedState.get<String>("calendarPeriod")?.let { CalendarPeriod.valueOf(it) } ?: CalendarPeriod.MONTH,
        selected = savedState.get<Long>("selectedDate")?.let(LocalDate::ofEpochDay) ?: clock.today(),
    ))

    private fun present(next: TaskPresentation) {
        savedState["taskView"] = next.view.name
        savedState["calendarPeriod"] = next.period.name
        savedState["selectedDate"] = next.selected.toEpochDay()
        presentation.value = next
    }

    fun selectView(view: TaskView) = present(presentation.value.copy(view = view))
    fun selectDate(date: LocalDate) = present(presentation.value.copy(selected = date))
    fun selectPeriod(period: CalendarPeriod) = present(presentation.value.copy(period = period))
    fun moveCalendar(steps: Long) = selectDate(TaskCalendarRules.move(presentation.value.selected, presentation.value.period, steps))
    fun goToday() = selectDate(clock.today())

    /** Re-emits when the date changes so "Today" stays correct across midnight. */
    private val today = flow {
        while (true) {
            emit(clock.today())
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val snapshot = combine(
        taskRepo.observeAll(),
        listRepo.observeLists(),
        tagRepo.observeTags(),
        taskRepo.observeTaskTags(),
    ) { tasks, lists, tags, refs -> TaskSnapshot(tasks, lists, tags, refs) }

    val state: StateFlow<TasksUiState> = combine(
        filter.filterNotNull(),
        combine(snapshot, presentation, prefs.settings) { snap, p, settings ->
            PresentedSnapshot(snap, p, settings.firstDayOfWeek)
        },
        combine(
            taskRepo.observeTrash(),
            listRepo.observeFolders(),
            reminderRepo.observeOwnerIds(ReminderOwner.TASK),
            timeRecords.observeRunning().map { running -> running.mapNotNull { it.taskId }.toSet() },
        ) { t, f, r, timing -> Extras(t, f, r, timing) },
        today,
    ) { rawFilter, presented, (trash, folders, reminderIds, timingIds), day ->
        val snap = presented.snapshot
        val zone = clock.zone()
        val current = when (rawFilter) {
            is TaskFilter.OfList -> rawFilter.takeIf { f -> snap.lists.any { it.id == f.listId } }
            is TaskFilter.OfTag -> rawFilter.takeIf { f -> snap.tags.any { it.id == f.tagId } }
            else -> rawFilter
        } ?: TaskFilter.Today
        val groups = if (current == TaskFilter.Trash) {
            TaskGrouping.trash(trash, snap.lists)
        } else {
            TaskGrouping.group(current, snap, day, zone)
        }
        val countFilters = listOf(TaskFilter.Inbox, TaskFilter.Today, TaskFilter.Next7Days, TaskFilter.All) +
            snap.lists.map { TaskFilter.OfList(it.id) } + snap.tags.map { TaskFilter.OfTag(it.id) }
        TasksUiState(
            loading = false,
            view = presented.presentation.view,
            calendarPeriod = presented.presentation.period,
            selectedDate = presented.presentation.selected,
            firstDayOfWeek = presented.firstDayOfWeek,
            calendarTasks = TaskCalendarRules.byDate(snap, day, zone),
            filter = current,
            groups = groups,
            lists = snap.lists,
            folders = folders,
            tags = snap.tags,
            counts = countFilters.associate { it.key() to TaskGrouping.openCount(it, snap, day, zone) } + mapOf(
                TaskFilter.Completed.key() to TaskGrouping.group(TaskFilter.Completed, snap, day, zone).sumOf { it.items.size },
                TaskFilter.Trash.key() to TaskGrouping.trash(trash, snap.lists).sumOf { it.items.size },
            ),
            today = day,
            zone = zone,
            reminderTaskIds = reminderIds,
            timingTaskIds = timingIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState(
        view = presentation.value.view,
        calendarPeriod = presentation.value.period,
        selectedDate = presentation.value.selected,
        today = clock.today(),
        zone = clock.zone(),
    ))

    init {
        viewModelScope.launch { filter.value = TaskFilter.fromKey(prefs.lastTaskFilter.first()) }
    }

    fun selectFilter(newFilter: TaskFilter) {
        filter.value = newFilter
        viewModelScope.launch { prefs.setLastTaskFilter(newFilter.key()) }
    }

    fun quickAddDefaults(): QuickAddDefaults {
        val s = state.value
        return when (val f = s.filter) {
            TaskFilter.Today, TaskFilter.Next7Days -> QuickAddDefaults(s.today, null, emptySet())
            is TaskFilter.OfList -> QuickAddDefaults(null, f.listId, emptySet())
            is TaskFilter.OfTag -> QuickAddDefaults(null, null, setOf(f.tagId))
            else -> QuickAddDefaults(null, null, emptySet())
        }
    }

    suspend fun addTask(draft: TaskDraft) {
        creator.create(draft)
    }

    private val _lastAdvance = MutableStateFlow<RepeatAdvance?>(null)
    /** Set after completing a repeating task, so the screen can offer to undo it. */
    val lastAdvance: StateFlow<RepeatAdvance?> = _lastAdvance

    fun setDone(id: Long, done: Boolean) = viewModelScope.launch {
        interactor.setDone(id, done)?.let { _lastAdvance.value = it }
    }

    fun undoRepeat(advance: RepeatAdvance) = viewModelScope.launch { interactor.undoRepeat(advance) }

    fun consumeAdvance() {
        _lastAdvance.value = null
    }

    fun moveToTrash(id: Long) = viewModelScope.launch { interactor.moveToTrash(id) }
    fun restore(id: Long) = viewModelScope.launch { interactor.restore(id) }
    fun deletePermanently(id: Long) = viewModelScope.launch { interactor.deletePermanently(id) }
    fun emptyTrash() = viewModelScope.launch { interactor.emptyTrash() }
}
