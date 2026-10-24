package com.meolist.feature.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoDatePickerDialog
import com.meolist.core.ui.MeoTimePickerDialog
import com.meolist.core.ui.durationText
import com.meolist.core.ui.longDate
import com.meolist.core.ui.timeLabel
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.toLocalDate
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.toMillis
import com.meolist.domain.common.today
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.RecordProblem
import com.meolist.domain.tracker.RecordSaveResult
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TimeRecordRepository
import com.meolist.domain.tracker.TimerInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val recordId: Long = 0,
    val typeId: Long = 0,
    val startAt: Long = 0,
    val endAt: Long = 0,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    val comment: String = "",
    val types: List<ActivityType> = emptyList(),
    val problem: RecordProblem? = null,
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0)
}

@HiltViewModel
class RecordEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val records: TimeRecordRepository,
    types: ActivityTypeRepository,
    private val tasks: TaskRepository,
    private val timers: TimerInteractor,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val recordId: Long = savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0
    private val presetTypeId: Long = savedStateHandle.get<Long>(Routes.ARG_EXTRA) ?: 0
    private val epochDay: Long = savedStateHandle.get<Long>(Routes.ARG_DAY) ?: Routes.NO_DAY

    private val _form = MutableStateFlow(
        RecordEditState(isNew = recordId == 0L, recordId = recordId, typeId = presetTypeId),
    )
    val form: StateFlow<RecordEditState> = _form

    private val _taskQuery = MutableStateFlow("")
    val taskQuery: StateFlow<String> = _taskQuery

    val taskResults: StateFlow<List<Task>> = combine(_taskQuery, tasks.observeAll()) { query, all ->
        val needle = query.trim()
        all.asSequence()
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
            .sortedWith(compareBy<Task> { it.isDone }.thenByDescending { it.updatedAt })
            .take(50)
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _form.value = if (recordId != 0L) existingRecord() else blankRecord()
        }
        viewModelScope.launch {
            types.observeTypes().collect { all ->
                _form.value = _form.value.let { s ->
                    val chosen = s.types.firstOrNull { it.id == s.typeId } ?: all.firstOrNull { it.id == s.typeId }
                    s.copy(
                        types = all,
                        typeId = when {
                            chosen != null -> chosen.id
                            s.typeId != 0L -> s.typeId
                            else -> all.firstOrNull { !it.archived }?.id ?: 0
                        },
                    )
                }
            }
        }
    }

    private suspend fun existingRecord(): RecordEditState {
        val record = records.getRecord(recordId) ?: return _form.value.copy(loaded = true, isNew = false)
        return _form.value.copy(
            loaded = true,
            isNew = false,
            typeId = record.typeId,
            startAt = record.startAt,
            endAt = record.endAt,
            taskId = record.taskId,
            taskTitle = record.taskId?.let { tasks.get(it)?.title },
            comment = record.comment,
        )
    }

    private fun blankRecord(): RecordEditState {
        val zone = clock.zone()
        val now = clock.now()
        val end = when {
            epochDay == Routes.NO_DAY -> now
            else -> {
                val day = LocalDate.ofEpochDay(epochDay)
                // Adding to an earlier day starts from an evening placeholder; today starts from now.
                if (day.isBefore(clock.today())) minOf(day.atTime(20, 0).toMillis(zone), now) else now
            }
        } / MINUTE * MINUTE
        val start = maxOf(end - 30 * MINUTE, 0)
        return _form.value.copy(loaded = true, startAt = start, endAt = end)
    }

    fun now(): Long = clock.now()
    fun zone(): ZoneId = clock.zone()

    fun setType(id: Long) = _form.value.let { _form.value = it.copy(typeId = id, problem = null) }

    fun setTask(task: Task?) = _form.value.let {
        _form.value = it.copy(taskId = task?.id, taskTitle = task?.title)
    }

    fun setComment(value: String) = _form.value.let { _form.value = it.copy(comment = value) }

    fun setStart(value: Long) = _form.value.let { s ->
        val duration = (s.endAt - s.startAt).coerceAtLeast(MINUTE)
        _form.value = s.copy(startAt = value, endAt = maxOf(s.endAt, value + duration), problem = null)
    }

    fun setEnd(value: Long) = _form.value.let { _form.value = it.copy(endAt = value, problem = null) }

    /** Sets the end to start + [minutes]. */
    fun setDuration(minutes: Int) = _form.value.let { s ->
        _form.value = s.copy(endAt = s.startAt + minutes * MINUTE, problem = null)
    }

    fun searchTasks(query: String) {
        _taskQuery.value = query
    }

    fun save(onSaved: () -> Unit) {
        val s = _form.value
        if (!s.loaded || !validate(s)) return
        viewModelScope.launch {
            val record = TimeRecord(
                id = if (s.isNew) 0 else s.recordId,
                typeId = s.typeId,
                taskId = s.taskId,
                startAt = s.startAt,
                endAt = s.endAt,
                comment = s.comment,
            )
            when (val result = timers.saveRecord(record)) {
                is RecordSaveResult.Saved -> onSaved()
                is RecordSaveResult.Invalid -> _form.value = _form.value.copy(problem = result.problem)
            }
        }
    }

    /** Client-side checks mirror the interactor so the message appears before anything is written. */
    private fun validate(s: RecordEditState): Boolean {
        val problem = when {
            s.typeId == 0L -> RecordProblem.NO_TYPE
            s.endAt <= s.startAt -> RecordProblem.NOT_POSITIVE
            s.endAt > clock.now() + MINUTE -> RecordProblem.IN_FUTURE
            else -> null
        }
        _form.value = s.copy(problem = problem)
        return problem == null
    }

    fun delete(onDeleted: () -> Unit) {
        if (recordId == 0L) return
        appScope.launch { timers.deleteRecord(recordId) }
        onDeleted()
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordEditRoute(
    onBack: () -> Unit,
    onAddActivityType: () -> Unit,
    vm: RecordEditViewModel = hiltViewModel(),
) {
    val state by vm.form.collectAsStateWithLifecycle()
    val taskQuery by vm.taskQuery.collectAsStateWithLifecycle()
    val taskResults by vm.taskResults.collectAsStateWithLifecycle()
    val zone = vm.zone()
    var pickStartDate by remember { mutableStateOf(false) }
    var pickStartTime by remember { mutableStateOf(false) }
    var pickEndDate by remember { mutableStateOf(false) }
    var pickEndTime by remember { mutableStateOf(false) }
    var pickTask by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val start = state.startAt.toLocalDateTime(zone)
    val end = state.endAt.toLocalDateTime(zone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.tracker_record_new else R.string.tracker_record_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meolist.core.R.string.core_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(com.meolist.core.R.string.core_delete))
                        }
                    }
                    IconButton(onClick = { vm.save(onBack) }, enabled = state.loaded) {
                        Icon(Icons.Outlined.Check, stringResource(com.meolist.core.R.string.core_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionLabel(stringResource(R.string.tracker_record_activity))
            if (state.types.none { !it.archived } && state.typeId == 0L) {
                Text(
                    stringResource(R.string.tracker_record_no_activities),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onAddActivityType, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.tracker_new_type))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.types.forEach { type ->
                    FilterChip(
                        selected = type.id == state.typeId,
                        onClick = { vm.setType(type.id) },
                        leadingIcon = {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(type.color)), contentAlignment = Alignment.Center) {
                                Icon(ActivityIcons.of(type.icon), null, tint = Color.White, modifier = Modifier.size(11.dp))
                            }
                        },
                        label = { Text(type.name) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.tracker_record_start))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickStartDate = true }, modifier = Modifier.weight(1f)) {
                    Text(longDate(start.toLocalDate(), com.meolist.core.ui.currentLocale()))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { pickStartTime = true }) { Text(timeLabel(start.toLocalTime())) }
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.tracker_record_end))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickEndDate = true }, modifier = Modifier.weight(1f)) {
                    Text(longDate(end.toLocalDate(), com.meolist.core.ui.currentLocale()))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { pickEndTime = true }) { Text(timeLabel(end.toLocalTime())) }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tracker_record_duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(durationText(state.durationMs), style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { minutes ->
                    AssistChip(
                        onClick = { vm.setDuration(minutes) },
                        label = { Text(durationText(minutes * 60_000L)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.tracker_record_task))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                headlineContent = {
                    Text(
                        state.taskTitle ?: stringResource(R.string.tracker_record_link_task),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (state.taskTitle == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                },
                trailingContent = if (state.taskId != null) {
                    { TextButton(onClick = { vm.setTask(null) }) { Text(stringResource(R.string.tracker_record_clear_task)) } }
                } else {
                    null
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { pickTask = true },
            )

            OutlinedTextField(
                value = state.comment,
                onValueChange = vm::setComment,
                label = { Text(stringResource(R.string.tracker_record_note)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            state.problem?.let { problem ->
                Text(
                    stringResource(
                        when (problem) {
                            RecordProblem.NO_TYPE -> R.string.tracker_record_problem_no_type
                            RecordProblem.NOT_POSITIVE -> R.string.tracker_record_problem_not_positive
                            RecordProblem.IN_FUTURE -> R.string.tracker_record_problem_in_future
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Button(
                onClick = { vm.save(onBack) },
                enabled = state.loaded,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) { Text(stringResource(com.meolist.core.R.string.core_save)) }
        }
    }

    if (pickStartDate) {
        MeoDatePickerDialog(
            initial = start.toLocalDate(),
            onConfirm = { date -> vm.setStart(date.atTime(start.toLocalTime()).toMillis(zone)) },
            onDismiss = { pickStartDate = false },
        )
    }
    if (pickStartTime) {
        MeoTimePickerDialog(
            initial = start.toLocalTime(),
            onConfirm = { time -> vm.setStart(start.toLocalDate().atTime(time).toMillis(zone)) },
            onDismiss = { pickStartTime = false },
        )
    }
    if (pickEndDate) {
        MeoDatePickerDialog(
            initial = end.toLocalDate(),
            onConfirm = { date -> vm.setEnd(date.atTime(end.toLocalTime()).toMillis(zone)) },
            onDismiss = { pickEndDate = false },
        )
    }
    if (pickEndTime) {
        MeoTimePickerDialog(
            initial = end.toLocalTime(),
            onConfirm = { time -> vm.setEnd(end.toLocalDate().atTime(time).toMillis(zone)) },
            onDismiss = { pickEndTime = false },
        )
    }
    if (pickTask) {
        TaskPickerDialog(
            query = taskQuery,
            results = taskResults,
            onQuery = vm::searchTasks,
            onPick = { vm.setTask(it); pickTask = false },
            onDismiss = { pickTask = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.tracker_record_delete_title),
            text = stringResource(R.string.tracker_record_delete_text),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun TaskPickerDialog(
    query: String,
    results: List<Task>,
    onQuery: (String) -> Unit,
    onPick: (Task) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracker_record_pick_task)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text(stringResource(R.string.tracker_record_task_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty()) {
                    Text(
                        stringResource(R.string.tracker_record_no_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                results.take(8).forEach { task ->
                    ListItem(
                        headlineContent = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            if (task.isDone) Icon(Icons.Outlined.Check, stringResource(R.string.tracker_record_task_done), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onPick(task) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
        },
    )
}
