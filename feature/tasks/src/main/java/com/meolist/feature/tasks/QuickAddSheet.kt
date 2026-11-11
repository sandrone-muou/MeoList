package com.meolist.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.meolist.core.theme.Meo
import com.meolist.core.ui.ColorDot
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.longDate
import com.meolist.core.ui.relativeDateLabel
import com.meolist.core.ui.rememberReminderPermissionState
import com.meolist.core.ui.timeLabel
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderKind
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.task.Priority
import com.meolist.domain.task.Tag
import com.meolist.domain.task.TaskDraft
import com.meolist.domain.task.TaskList
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

typealias AddTaskCallback = suspend (TaskDraft) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddSheet(
    defaults: QuickAddDefaults,
    lists: List<TaskList>,
    tags: List<Tag>,
    today: LocalDate,
    onAdd: AddTaskCallback,
    onDismiss: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !busy },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        TaskEntryForm(defaults, lists, tags, today, onAdd,
            onBusy = { busy = it }, onCancel = { if (!busy) onDismiss() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskEntryForm(
    defaults: QuickAddDefaults,
    lists: List<TaskList>,
    tags: List<Tag>,
    today: LocalDate,
    onAdd: AddTaskCallback,
    onBusy: (Boolean) -> Unit = {},
    onSaved: (LocalDate?) -> Unit = {},
    showHeading: Boolean = true,
    onCancel: (() -> Unit)? = null,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var dateValue by rememberSaveable { mutableStateOf(defaults.date?.toString()) }
    var timeValue by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityValue by rememberSaveable { mutableIntStateOf(Priority.NONE.value) }
    var listId by rememberSaveable { mutableStateOf(defaults.listId) }
    var tagValues by rememberSaveable { mutableStateOf(defaults.tagIds.toLongArray()) }
    var reminderKind by rememberSaveable { mutableStateOf<Int?>(null) }
    var reminderOffset by rememberSaveable { mutableIntStateOf(0) }
    var reminderMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var scheduleOpen by rememberSaveable { mutableStateOf(false) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val permissions = rememberReminderPermissionState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locale = currentLocale()
    val date = dateValue?.let(LocalDate::parse)
    val time = timeValue?.let(LocalTime::parse)
    val priority = Priority.fromValue(priorityValue)
    val tagIds = tagValues.toSet()
    val reminder = reminderKind?.let {
        Reminder(ownerType = ReminderOwner.TASK, ownerId = 0,
            kind = ReminderKind.fromValue(it),
            offsetMin = reminderOffset, minuteOfDay = reminderMinute)
    }
    val submit: () -> Unit = {
        if (title.isNotBlank() && !submitting) {
            submitting = true
            onBusy(true)
            failed = false
            savedMessage = null
            val draft = TaskDraft(title, date, time, priority, listId, tagIds, reminder.takeIf { date != null })
            scope.launch {
                try {
                    onAdd(draft)
                    title = ""
                    val savedDate = draft.date
                    savedMessage = if (savedDate == null) context.getString(R.string.tasks_created_no_date)
                        else context.getString(R.string.tasks_created_for_date, longDate(savedDate, locale))
                    onSaved(draft.date)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally {
                    submitting = false
                    onBusy(false)
                }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = Meo.spacing.screen).padding(bottom = Meo.spacing.lg).imePadding(),
        verticalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
    ) {
        if (showHeading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_new), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onCancel != null) {
                    IconButton(onClick = onCancel, enabled = !submitting) {
                        Icon(Icons.Outlined.Close, stringResource(com.meolist.core.R.string.core_cancel))
                    }
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; failed = false; savedMessage = null },
            placeholder = { Text(stringResource(R.string.tasks_add_hint)) },
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("task-entry-title"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
            ScheduleChip(date, time, today, enabled = !submitting) { scheduleOpen = true }
            PriorityChip(priority, enabled = !submitting) { priorityValue = it.value }
            ListChip(listId, lists, enabled = !submitting) { listId = it }
            TagChip(tags, tagIds, enabled = !submitting) { tagsOpen = true }
        }
        if (failed) {
            Text(stringResource(R.string.tasks_save_failed), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        savedMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(onClick = submit, enabled = title.isNotBlank() && !submitting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (submitting) R.string.tasks_saving else R.string.tasks_add))
        }
    }
    if (scheduleOpen) {
        QuickScheduleSheet(
            initialDate = date,
            initialTime = time,
            initialReminder = reminder,
            today = today,
            onConfirm = { pickedDate, pickedTime, pickedReminder ->
                dateValue = pickedDate?.toString()
                timeValue = pickedTime?.toString()
                reminderKind = pickedReminder?.kind?.value
                reminderOffset = pickedReminder?.offsetMin ?: 0
                reminderMinute = pickedReminder?.minuteOfDay
                if (pickedReminder != null) permissions.requestNotificationsIfNeeded()
            },
            onDismiss = { scheduleOpen = false },
        )
    }
    if (tagsOpen) {
        QuickTagSheet(tags, tagIds, onConfirm = { tagValues = it.toLongArray() }, onDismiss = { tagsOpen = false })
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun ScheduleChip(
    date: LocalDate?,
    time: LocalTime?,
    today: LocalDate,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val label = when {
        date == null -> stringResource(R.string.tasks_due_date)
        time == null -> relativeDateLabel(date, today)
        else -> relativeDateLabel(date, today) + " " + timeLabel(time)
    }
    AssistChip(
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                Icons.Outlined.CalendarToday,
                null,
                Modifier.size(AssistChipDefaults.IconSize),
                tint = if (date != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        },
    )
}

@Composable
internal fun PriorityChip(priority: Priority, enabled: Boolean = true, onChange: (Priority) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            enabled = enabled,
            onClick = { menu = true },
            label = { Text(priorityLabel(priority)) },
            leadingIcon = {
                Icon(Icons.Outlined.Flag, null, Modifier.size(AssistChipDefaults.IconSize), tint = priorityColor(priority))
            },
        )
        PriorityMenu(menu, onDismiss = { menu = false }, onSelect = { onChange(it); menu = false })
    }
}

@Composable
internal fun PriorityMenu(expanded: Boolean, onDismiss: () -> Unit, onSelect: (Priority) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Priority.entries.reversed().forEach { p ->
            DropdownMenuItem(
                text = { Text(priorityLabel(p)) },
                leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = priorityColor(p)) },
                onClick = { onSelect(p) },
            )
        }
    }
}

@Composable
private fun ListChip(listId: Long?, lists: List<TaskList>, enabled: Boolean = true, onChange: (Long?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val current = lists.firstOrNull { it.id == listId }
    Box {
        AssistChip(
            enabled = enabled,
            onClick = { menu = true },
            label = { Text(current?.name ?: stringResource(R.string.tasks_filter_inbox)) },
            leadingIcon = {
                if (current != null) ColorDot(current.color) else Icon(Icons.AutoMirrored.Outlined.List, null, Modifier.size(AssistChipDefaults.IconSize))
            },
        )
        ListMenu(menu, lists, onDismiss = { menu = false }, onSelect = { onChange(it); menu = false })
    }
}

@Composable
internal fun ListMenu(expanded: Boolean, lists: List<TaskList>, onDismiss: () -> Unit, onSelect: (Long?) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tasks_filter_inbox)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.List, null) },
            onClick = { onSelect(null) },
        )
        lists.forEach { list ->
            DropdownMenuItem(
                text = { Text(list.name) },
                leadingIcon = { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { ColorDot(list.color) } },
                onClick = { onSelect(list.id) },
            )
        }
    }
}

@Composable
private fun TagChip(tags: List<Tag>, selected: Set<Long>, enabled: Boolean = true, onClick: () -> Unit) {
    val picked = tags.filter { it.id in selected }
    val label = when {
        picked.isEmpty() -> stringResource(R.string.tasks_tags)
        picked.size == 1 -> "#" + picked.first().name
        else -> "#" + picked.first().name + " +" + (picked.size - 1)
    }
    AssistChip(
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            if (picked.isNotEmpty()) {
                ColorDot(picked.first().color)
            } else {
                Icon(Icons.Outlined.Tag, null, Modifier.size(AssistChipDefaults.IconSize))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickTagSheet(
    tags: List<Tag>,
    initial: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tasks_tags),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { selected = emptySet() }) {
                        Text(stringResource(com.meolist.core.R.string.core_clear))
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(com.meolist.core.R.string.core_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (tags.isEmpty()) {
                Text(
                    stringResource(R.string.tasks_no_tags),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in selected,
                            onClick = {
                                selected = if (tag.id in selected) selected - tag.id else selected + tag.id
                            },
                            label = { Text(tag.name) },
                            leadingIcon = { ColorDot(tag.color) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onConfirm(selected)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(com.meolist.core.R.string.core_ok))
            }
        }
    }
}
