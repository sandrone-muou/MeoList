package com.meolist.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.meolist.core.ui.ColorDot
import com.meolist.core.ui.MeoTimePickerDialog
import com.meolist.core.ui.relativeDateLabel
import com.meolist.core.ui.rememberReminderPermissionState
import com.meolist.core.ui.timeLabel
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.task.Priority
import com.meolist.domain.task.Tag
import com.meolist.domain.task.TaskList
import java.time.LocalDate
import java.time.LocalTime

typealias AddTaskCallback = (
    title: String,
    date: LocalDate?,
    time: LocalTime?,
    priority: Priority,
    listId: Long?,
    tagIds: Set<Long>,
    reminder: Reminder?,
) -> Unit

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaults.date) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var priority by remember { mutableStateOf(Priority.NONE) }
    var listId by remember { mutableStateOf(defaults.listId) }
    var tagIds by remember { mutableStateOf(defaults.tagIds) }
    var reminder by remember { mutableStateOf<Reminder?>(null) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    val permissions = rememberReminderPermissionState()
    val focus = remember { FocusRequester() }

    val submit = {
        if (title.isNotBlank()) {
            onAdd(title, date, time, priority, listId, tagIds, reminder.takeIf { date != null })
            title = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.tasks_add_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                IconButton(onClick = submit, enabled = title.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.tasks_add))
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScheduleChip(date, time, today) { scheduleOpen = true }
                PriorityChip(priority) { priority = it }
                ListChip(listId, lists) { listId = it }
                TagChip(tags, tagIds) { tagsOpen = true }
            }
        }
    }
    if (scheduleOpen) {
        QuickScheduleSheet(
            initialDate = date,
            initialTime = time,
            initialReminder = reminder,
            today = today,
            onConfirm = { pickedDate, pickedTime, pickedReminder ->
                date = pickedDate
                time = pickedTime
                reminder = pickedReminder
                if (pickedReminder != null) permissions.requestNotificationsIfNeeded()
            },
            onDismiss = { scheduleOpen = false },
        )
    }
    if (tagsOpen) {
        QuickTagSheet(
            tags = tags,
            initial = tagIds,
            onConfirm = { tagIds = it },
            onDismiss = { tagsOpen = false },
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun ScheduleChip(
    date: LocalDate?,
    time: LocalTime?,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val label = when {
        date == null -> stringResource(R.string.tasks_due_date)
        time == null -> relativeDateLabel(date, today)
        else -> relativeDateLabel(date, today) + " " + timeLabel(time)
    }
    AssistChip(
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
internal fun PriorityChip(priority: Priority, onChange: (Priority) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        AssistChip(
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
private fun ListChip(listId: Long?, lists: List<TaskList>, onChange: (Long?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val current = lists.firstOrNull { it.id == listId }
    Box {
        AssistChip(
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
private fun TagChip(tags: List<Tag>, selected: Set<Long>, onClick: () -> Unit) {
    val picked = tags.filter { it.id in selected }
    val label = when {
        picked.isEmpty() -> stringResource(R.string.tasks_tags)
        picked.size == 1 -> "#" + picked.first().name
        else -> "#" + picked.first().name + " +" + (picked.size - 1)
    }
    AssistChip(
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
