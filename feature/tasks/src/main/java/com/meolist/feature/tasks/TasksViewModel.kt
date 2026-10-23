package com.meolist.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.ListFolder
import com.meolist.domain.task.Priority
import com.meolist.domain.task.Tag
import com.meolist.domain.task.TagRepository
import com.meolist.domain.task.TaskFilter
import com.meolist.domain.task.TaskGroup
import com.meolist.domain.task.TaskGrouping
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.task.TaskList
import com.meolist.domain.task.TaskListRepository
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.task.TaskSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class TasksUiState(
    val loading: Boolean = true,
    val filter: TaskFilter = TaskFilter.Today,
    val groups: List<TaskGroup> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val folders: List<ListFolder> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** Open task counts keyed by [TaskFilter.key]. */
    val counts: Map<String, Int> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val isTrash: Boolean get() = filter == TaskFilter.Trash
}

/** Defaults for the quick-add sheet derived from the current filter. */
data class QuickAddDefaults(val date: LocalDate?, val listId: Long?, val tagIds: Set<Long>)

@HiltViewModel
class TasksViewModel @Inject constructor(
    taskRepo: TaskRepository,
    listRepo: TaskListRepository,
    tagRepo: TagRepository,
    private val interactor: TaskInteractor,
    private val prefs: AppPreferences,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow<TaskFilter?>(null)

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
        snapshot,
        taskRepo.observeTrash(),
        listRepo.observeFolders(),
        today,
    ) { rawFilter, snap, trash, folders, day ->
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
            filter = current,
            groups = groups,
            lists = snap.lists,
            folders = folders,
            tags = snap.tags,
            counts = countFilters.associate { it.key() to TaskGrouping.openCount(it, snap, day, zone) },
            today = day,
            zone = zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

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

    fun addTask(
        title: String,
        date: LocalDate?,
        time: LocalTime?,
        priority: Priority,
        listId: Long?,
        tagIds: Set<Long>,
    ) {
        if (title.isBlank()) return
        val zone = clock.zone()
        val dueAt = date?.let { dueMillis(it, time, zone) }
        viewModelScope.launch {
            interactor.add(
                title = title,
                listId = listId,
                dueAt = dueAt,
                allDay = time == null,
                priority = priority,
                tagIds = tagIds,
            )
        }
    }

    fun setDone(id: Long, done: Boolean) = viewModelScope.launch { interactor.setDone(id, done) }
    fun moveToTrash(id: Long) = viewModelScope.launch { interactor.moveToTrash(id) }
    fun restore(id: Long) = viewModelScope.launch { interactor.restore(id) }
    fun deletePermanently(id: Long) = viewModelScope.launch { interactor.deletePermanently(id) }
    fun emptyTrash() = viewModelScope.launch { interactor.emptyTrash() }
}
