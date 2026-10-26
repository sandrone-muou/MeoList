package com.meolist.feature.tasks.detail

import com.meolist.core.ui.components.MeoScaffold
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meolist.core.ui.ColorDot
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoDatePickerDialog
import com.meolist.core.ui.MeoPalette
import com.meolist.core.ui.MeoTimePickerDialog
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.longDate
import com.meolist.core.ui.timeLabel
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.task.Tag
import com.meolist.domain.task.Task
import com.meolist.feature.tasks.ListMenu
import com.meolist.feature.tasks.PriorityMenu
import com.meolist.feature.tasks.R
import com.meolist.feature.tasks.RepeatAdvanceEffect
import com.meolist.feature.tasks.TaskCheckbox
import com.meolist.feature.tasks.dueLabel
import com.meolist.feature.tasks.priorityColor
import com.meolist.feature.tasks.priorityLabel
import java.time.LocalTime

@Composable
fun TaskDetailRoute(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onAddActivityType: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.notFound) { if (state.notFound) onBack() }
    val task = state.task ?: return
    TaskDetailScreen(state, task, viewModel, onBack, onOpenTask, onAddActivityType)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailScreen(
    state: TaskDetailState,
    task: Task,
    vm: TaskDetailViewModel,
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onAddActivityType: () -> Unit,
) {
    val editable = !task.isDeleted
    val timer by vm.timer.collectAsStateWithLifecycle()
    var confirmDeleteForever by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val advance by vm.lastAdvance.collectAsStateWithLifecycle()
    RepeatAdvanceEffect(advance, snackbar, state.today, state.zone, onUndo = vm::undoRepeat, onConsumed = vm::consumeAdvance)

    MeoScaffold(
        title = "",
        onBack = onBack,
        actions = {
            if (task.isDeleted) {
                IconButton(onClick = { vm.restore() }) {
                    Icon(Icons.Outlined.RestoreFromTrash, stringResource(R.string.tasks_restore))
                }
                IconButton(onClick = { confirmDeleteForever = true }) {
                    Icon(Icons.Outlined.DeleteForever, stringResource(R.string.tasks_delete_forever))
                }
            } else {
                IconButton(onClick = { vm.moveToTrash(); onBack() }) {
                    Icon(Icons.Outlined.Delete, stringResource(com.meolist.core.R.string.core_delete))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            if (task.isDeleted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.tasks_in_trash_banner),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            state.parentTitle?.let { parent ->
                Text(
                    stringResource(R.string.tasks_subtask_of, parent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { task.parentId?.let(onOpenTask) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            TitleRow(task, editable, onDone = vm::setDone, onTitle = vm::setTitle)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            DueRow(state, task, editable, vm)
            if (task.parentId == null) {
                RepeatRow(task, state.today, task.dueDate(state.zone), editable, vm::setRepeat)
            }
            ReminderRow(task, state.reminders, editable, vm::setReminders)
            TimerRow(timer, canSetListDefault = task.listId != null, editable = editable, vm = vm, onAddActivityType = onAddActivityType)
            PriorityRow(task, editable, vm)
            if (task.parentId == null) {
                ListRow(state, task, editable, vm)
                TagsRow(state, editable, vm)
            }

            NoteField(task, editable, vm::setNote)

            if (task.parentId == null) {
                SubtaskSection(state, editable, vm, onOpenTask)
            }

            Footer(task, state)
        }
    }

    if (confirmDeleteForever) {
        ConfirmDialog(
            title = stringResource(R.string.tasks_delete_forever),
            text = stringResource(R.string.tasks_delete_forever_message),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { vm.deleteForever(); onBack() },
            onDismiss = { confirmDeleteForever = false },
        )
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun TitleRow(task: Task, editable: Boolean, onDone: (Boolean) -> Unit, onTitle: (String) -> Unit) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    Row(Modifier.padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskCheckbox(task.isDone, task.priority, onDone, enabled = editable)
        TextField(
            value = title,
            onValueChange = { title = it; onTitle(it) },
            enabled = editable,
            placeholder = { Text(stringResource(R.string.tasks_title_hint)) },
            textStyle = MaterialTheme.typography.titleLarge.copy(
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            ),
            colors = transparentFieldColors(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    iconTint: Color? = null,
    valueColor: Color? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null, tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                trailing?.invoke()
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun DueRow(state: TaskDetailState, task: Task, editable: Boolean, vm: TaskDetailViewModel) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val dueDateTime = task.dueAt?.toLocalDateTime(state.zone)
    val overdue = !task.isDone && dueDateTime != null && dueDateTime.toLocalDate() < state.today

    DetailRow(
        icon = Icons.Outlined.CalendarToday,
        label = stringResource(R.string.tasks_due_date),
        value = task.dueAt?.let { dueLabel(it, task.allDay, state.today, state.zone) } ?: stringResource(R.string.tasks_none),
        valueColor = if (overdue) MaterialTheme.colorScheme.error else null,
        enabled = editable,
        onClick = { pickDate = true },
        trailing = {
            if (task.dueAt != null && editable) {
                IconButton(onClick = { pickTime = true }) {
                    Icon(Icons.Outlined.Schedule, stringResource(R.string.tasks_set_time))
                }
                IconButton(onClick = { vm.setDue(null, null) }) {
                    Icon(Icons.Outlined.Close, stringResource(com.meolist.core.R.string.core_clear))
                }
            }
        },
    )
    if (pickDate) {
        MeoDatePickerDialog(
            initial = dueDateTime?.toLocalDate() ?: state.today,
            onConfirm = { date -> vm.setDue(date, if (task.allDay) null else dueDateTime?.toLocalTime()) },
            onDismiss = { pickDate = false },
        )
    }
    if (pickTime && dueDateTime != null) {
        MeoTimePickerDialog(
            initial = if (task.allDay) LocalTime.of(9, 0) else dueDateTime.toLocalTime(),
            onConfirm = { time -> vm.setDue(dueDateTime.toLocalDate(), time) },
            onDismiss = { pickTime = false },
            onClear = { vm.setDue(dueDateTime.toLocalDate(), null) },
        )
    }
}

@Composable
private fun PriorityRow(task: Task, editable: Boolean, vm: TaskDetailViewModel) {
    var menu by remember { mutableStateOf(false) }
    Box {
        DetailRow(
            icon = Icons.Outlined.Flag,
            label = stringResource(R.string.tasks_priority),
            value = priorityLabel(task.priority),
            iconTint = priorityColor(task.priority),
            enabled = editable,
            onClick = { menu = true },
        )
        Box(Modifier.align(Alignment.TopEnd)) {
            PriorityMenu(menu, onDismiss = { menu = false }, onSelect = { vm.setPriority(it); menu = false })
        }
    }
}

@Composable
private fun ListRow(state: TaskDetailState, task: Task, editable: Boolean, vm: TaskDetailViewModel) {
    var menu by remember { mutableStateOf(false) }
    val list = state.lists.firstOrNull { it.id == task.listId }
    Box {
        DetailRow(
            icon = Icons.AutoMirrored.Outlined.List,
            label = stringResource(R.string.tasks_list),
            value = list?.name ?: stringResource(R.string.tasks_filter_inbox),
            iconTint = list?.let { Color(it.color) },
            enabled = editable,
            onClick = { menu = true },
        )
        Box(Modifier.align(Alignment.TopEnd)) {
            ListMenu(menu, state.lists, onDismiss = { menu = false }, onSelect = { vm.setList(it); menu = false })
        }
    }
}

@Composable
private fun TagsRow(state: TaskDetailState, editable: Boolean, vm: TaskDetailViewModel) {
    var dialog by remember { mutableStateOf(false) }
    val selected = state.tags.filter { it.id in state.tagIds }
    DetailRow(
        icon = Icons.Outlined.Tag,
        label = stringResource(R.string.tasks_tags),
        value = if (selected.isEmpty()) stringResource(R.string.tasks_none) else selected.joinToString(" ") { "#${it.name}" },
        enabled = editable,
        onClick = { dialog = true },
    )
    if (dialog) {
        TagPickerDialog(
            tags = state.tags,
            initial = state.tagIds,
            onCreate = vm::createTag,
            onConfirm = vm::setTags,
            onDismiss = { dialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerDialog(
    tags: List<Tag>,
    initial: Set<Long>,
    onCreate: (String, Int, (Long) -> Unit) -> Unit,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var newTag by remember { mutableStateOf("") }
    val create = {
        val name = newTag.trim()
        if (name.isNotEmpty()) {
            onCreate(name, MeoPalette.colors[(tags.size + 3) % MeoPalette.colors.size]) { id -> selected = selected + id }
            newTag = ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_tags)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in selected,
                            onClick = { selected = if (tag.id in selected) selected - tag.id else selected + tag.id },
                            label = { Text(tag.name) },
                            leadingIcon = { ColorDot(tag.color, size = 8.dp) },
                        )
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text(stringResource(R.string.tasks_new_tag)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    trailingIcon = {
                        IconButton(onClick = create, enabled = newTag.isNotBlank()) {
                            Icon(Icons.Outlined.Add, stringResource(R.string.tasks_new_tag))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected); onDismiss() }) { Text(stringResource(com.meolist.core.R.string.core_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
        },
    )
}

@Composable
private fun NoteField(task: Task, editable: Boolean, onNote: (String) -> Unit) {
    var note by rememberSaveable(task.id) { mutableStateOf(task.note) }
    TextField(
        value = note,
        onValueChange = { note = it; onNote(it) },
        enabled = editable,
        placeholder = { Text(stringResource(R.string.tasks_note_hint)) },
        colors = transparentFieldColors(),
        minLines = 2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

@Composable
private fun SubtaskSection(state: TaskDetailState, editable: Boolean, vm: TaskDetailViewModel, onOpenTask: (Long) -> Unit) {
    var newSubtask by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Task?>(null) }
    val done = state.subtasks.count { it.isDone }

    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    Text(
        if (state.subtasks.isEmpty()) stringResource(R.string.tasks_subtasks)
        else stringResource(R.string.tasks_subtasks_progress, done, state.subtasks.size),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    state.subtasks.forEach { sub ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = editable) { renaming = sub }
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCheckbox(sub.isDone, sub.priority, { vm.setSubtaskDone(sub.id, it) }, enabled = editable)
            Text(
                sub.title,
                modifier = Modifier.weight(1f),
                color = if (sub.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (sub.isDone) TextDecoration.LineThrough else null,
            )
            if (editable) {
                IconButton(onClick = { vm.deleteSubtask(sub.id) }) {
                    Icon(Icons.Outlined.Close, stringResource(com.meolist.core.R.string.core_delete), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
    if (editable) {
        val add = {
            vm.addSubtask(newSubtask)
            newSubtask = ""
        }
        OutlinedTextField(
            value = newSubtask,
            onValueChange = { newSubtask = it },
            placeholder = { Text(stringResource(R.string.tasks_add_subtask)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            leadingIcon = { Icon(Icons.Outlined.Add, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    renaming?.let { sub ->
        RenameDialog(
            initial = sub.title,
            onOpen = { onOpenTask(sub.id) },
            onConfirm = { vm.renameSubtask(sub, it) },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onOpen: () -> Unit, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_edit_subtask)) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                AssistChip(onClick = { onDismiss(); onOpen() }, label = { Text(stringResource(R.string.tasks_open_details)) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text); onDismiss() }, enabled = text.isNotBlank()) {
                Text(stringResource(com.meolist.core.R.string.core_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) } },
    )
}

@Composable
private fun Footer(task: Task, state: TaskDetailState) {
    val locale = currentLocale()
    val created = task.createdAt.toLocalDateTime(state.zone)
    Column(Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.tasks_created_at, "${longDate(created.toLocalDate(), locale)} ${timeLabel(created.toLocalTime())}"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        task.completedAt?.let {
            val completed = it.toLocalDateTime(state.zone)
            Text(
                stringResource(R.string.tasks_completed_at, "${longDate(completed.toLocalDate(), locale)} ${timeLabel(completed.toLocalTime())}"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.size(24.dp))
    }
}
