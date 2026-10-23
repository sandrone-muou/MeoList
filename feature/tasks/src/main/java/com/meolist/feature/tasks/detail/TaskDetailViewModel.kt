package com.meolist.feature.tasks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meolist.core.navigation.Routes
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.reminder.ReminderRepository
import com.meolist.domain.repeat.RepeatRule
import com.meolist.domain.task.Priority
import com.meolist.domain.task.RepeatAdvance
import com.meolist.domain.task.Tag
import com.meolist.domain.task.TagRepository
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.task.TaskList
import com.meolist.domain.task.TaskListRepository
import com.meolist.domain.task.TaskRepository
import com.meolist.feature.tasks.dueMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class TaskDetailState(
    val task: Task? = null,
    val notFound: Boolean = false,
    val parentTitle: String? = null,
    val subtasks: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val tagIds: Set<Long> = emptySet(),
    val reminders: List<Reminder> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: TaskRepository,
    listRepo: TaskListRepository,
    private val tagRepo: TagRepository,
    private val interactor: TaskInteractor,
    reminderRepo: ReminderRepository,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val taskId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_ID))

    /** Latest user intent. Text fields are edited here and saved with a short debounce. */
    private val draft = MutableStateFlow<Task?>(null)
    private val notFound = MutableStateFlow(false)
    private var persistedTitle = ""
    private var pendingSave: Job? = null

    init {
        viewModelScope.launch {
            repo.observeTask(taskId).collect { stored ->
                if (stored == null) {
                    notFound.value = true
                    return@collect
                }
                persistedTitle = stored.title
                val current = draft.value
                // Keep in-progress text edits; take everything else from the database.
                draft.value = if (current == null) stored else stored.copy(title = current.title, note = current.note)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val parentTitle = draft.map { it?.parentId }.flatMapLatest { parentId ->
        if (parentId == null) flowOf(null) else repo.observeTask(parentId).map { it?.title }
    }

    val state: StateFlow<TaskDetailState> = combine(
        combine(draft, notFound, parentTitle) { t, nf, pt -> Triple(t, nf, pt) },
        repo.observeSubtasks(taskId),
        listRepo.observeLists(),
        tagRepo.observeTags(),
        combine(repo.observeTagIds(taskId), reminderRepo.observeForOwner(ReminderOwner.TASK, taskId)) { ids, r -> ids to r },
    ) { (task, nf, parent), subtasks, lists, tags, (tagIds, reminders) ->
        TaskDetailState(
            task = task,
            notFound = nf,
            parentTitle = parent,
            subtasks = subtasks,
            lists = lists.filter { !it.archived || it.id == task?.listId },
            tags = tags,
            tagIds = tagIds.toSet(),
            reminders = reminders,
            today = clock.today(),
            zone = clock.zone(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailState())

    fun setTitle(title: String) = editText { it.copy(title = title) }
    fun setNote(note: String) = editText { it.copy(note = note) }

    fun setDue(date: LocalDate?, time: LocalTime?) = edit {
        if (date == null) it.copy(dueAt = null, allDay = true)
        else it.copy(dueAt = dueMillis(date, time, clock.zone()), allDay = time == null)
    }

    fun setPriority(priority: Priority) = edit { it.copy(priority = priority) }
    fun setList(listId: Long?) = edit { it.copy(listId = listId) }

    /** Set after completing a repeating task, so the screen can offer to undo it. */
    private val _lastAdvance = MutableStateFlow<RepeatAdvance?>(null)
    val lastAdvance: StateFlow<RepeatAdvance?> = _lastAdvance

    fun setDone(done: Boolean) {
        flush()
        appScope.launch { interactor.setDone(taskId, done)?.let { _lastAdvance.value = it } }
    }

    fun undoRepeat(advance: RepeatAdvance) {
        appScope.launch { interactor.undoRepeat(advance) }
    }

    fun consumeAdvance() {
        _lastAdvance.value = null
    }

    /** Repeating needs a date to count from: without one the task becomes due today. */
    fun setRepeat(rule: RepeatRule?, fromCompletion: Boolean) = edit { task ->
        val withRule = task.copy(repeatRule = rule?.toRRule(), repeatFromCompletion = rule != null && fromCompletion)
        if (rule != null && withRule.dueAt == null) {
            withRule.copy(dueAt = dueMillis(clock.today(), null, clock.zone()), allDay = true)
        } else {
            withRule
        }
    }

    fun setReminders(reminders: List<Reminder>) {
        flush()
        appScope.launch { interactor.setReminders(taskId, reminders) }
    }

    fun setTags(tagIds: Set<Long>) {
        appScope.launch { interactor.setTags(taskId, tagIds) }
    }

    fun createTag(name: String, color: Int, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val existing = state.value.tags.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            onCreated(existing?.id ?: tagRepo.saveTag(Tag(name = name, color = color)))
        }
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { interactor.add(title = title, parentId = taskId) }
    }

    fun setSubtaskDone(id: Long, done: Boolean) = viewModelScope.launch {
        interactor.setDone(id, done)?.let { _lastAdvance.value = it }
    }

    fun renameSubtask(subtask: Task, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { interactor.save(subtask.copy(title = title)) }
    }

    fun deleteSubtask(id: Long) = viewModelScope.launch { interactor.moveToTrash(id) }

    fun moveToTrash() {
        flush()
        appScope.launch { interactor.moveToTrash(taskId) }
    }

    fun restore() = appScope.launch { interactor.restore(taskId) }
    fun deleteForever() = appScope.launch { interactor.deletePermanently(taskId) }

    private fun edit(transform: (Task) -> Task) {
        val current = draft.value ?: return
        draft.value = transform(current)
        flush(force = true)
    }

    private fun editText(transform: (Task) -> Task) {
        val current = draft.value ?: return
        draft.value = transform(current)
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(400)
            pendingSave = null
            persist()
        }
    }

    /** Saves immediately if a debounced save is pending (or always when [force]). */
    private fun flush(force: Boolean = false) {
        if (pendingSave == null && !force) return
        pendingSave?.cancel()
        pendingSave = null
        persist()
    }

    private fun persist() {
        val task = draft.value ?: return
        val toSave = if (task.title.isBlank()) task.copy(title = persistedTitle) else task
        appScope.launch { interactor.save(toSave) }
    }

    override fun onCleared() {
        flush()
    }
}
