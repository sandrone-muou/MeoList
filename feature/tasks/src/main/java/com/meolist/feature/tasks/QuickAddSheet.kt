package com.meolist.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.meolist.core.ui.ColorDot
import com.meolist.core.ui.MeoDatePickerDialog
import com.meolist.core.ui.relativeDateLabel
import com.meolist.core.ui.rememberReminderPermissionState
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.task.Priority
import com.meolist.domain.task.TaskList
import com.meolist.feature.tasks.detail.reminderLabel
import java.time.LocalDate
import java.time.LocalTime

typealias AddTaskCallback = (
    title: String,
    date: LocalDate?,
    time: LocalTime?,
    priority: Priority,
    listId: Long?,
    tagIds: Set<Long>,
    reminderDaysBefore: Int?,
) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddSheet(
    defaults: QuickAddDefaults,
    lists: List<TaskList>,
    today: LocalDate,
    onAdd: AddTaskCallback,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaults.date) }
    var priority by remember { mutableStateOf(Priority.NONE) }
    var listId by remember { mutableStateOf(defaults.listId) }
    var reminderDaysBefore by remember { mutableStateOf<Int?>(null) }
    val permissions = rememberReminderPermissionState()
    val focus = remember { FocusRequester() }

    val submit = {
        if (title.isNotBlank()) {
            onAdd(title, date, null, priority, listId, defaults.tagIds, reminderDaysBefore.takeIf { date != null })
            title = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateChip(date, today) {
                    date = it
                    if (it == null) reminderDaysBefore = null
                }
                PriorityChip(priority) { priority = it }
                ListChip(listId, lists) { listId = it }
                ReminderChip(reminderDaysBefore) { days ->
                    if (days != null) {
                        if (date == null) date = today
                        permissions.requestNotificationsIfNeeded()
                    }
                    reminderDaysBefore = days
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun DateChip(date: LocalDate?, today: LocalDate, onChange: (LocalDate?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { menu = true },
            label = { Text(date?.let { relativeDateLabel(it, today) } ?: stringResource(R.string.tasks_due_date)) },
            leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(com.meolist.core.R.string.core_today)) }, onClick = { onChange(today); menu = false })
            DropdownMenuItem(text = { Text(stringResource(com.meolist.core.R.string.core_tomorrow)) }, onClick = { onChange(today.plusDays(1)); menu = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.tasks_next_week)) }, onClick = { onChange(today.plusDays(7)); menu = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.tasks_pick_date)) }, onClick = { picker = true; menu = false })
            DropdownMenuItem(text = { Text(stringResource(R.string.tasks_no_date)) }, onClick = { onChange(null); menu = false })
        }
    }
    if (picker) {
        MeoDatePickerDialog(initial = date ?: today, onConfirm = onChange, onDismiss = { picker = false })
    }
}

@Composable
private fun ReminderChip(daysBefore: Int?, onChange: (Int?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val reminder = daysBefore?.let {
        Reminder(
            ownerType = ReminderOwner.TASK,
            ownerId = 0,
            offsetMin = it * Reminder.DAY_MINUTES,
            minuteOfDay = Reminder.DEFAULT_MINUTE_OF_DAY,
        )
    }
    Box {
        AssistChip(
            onClick = { menu = true },
            label = { Text(reminder?.let { reminderLabel(it, allDay = true) } ?: stringResource(R.string.tasks_reminders)) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Alarm,
                    null,
                    Modifier.size(AssistChipDefaults.IconSize),
                    tint = if (reminder != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tasks_none)) },
                onClick = { onChange(null); menu = false },
            )
            listOf(0, 1, 7).forEach { days ->
                val option = Reminder(
                    ownerType = ReminderOwner.TASK,
                    ownerId = 0,
                    offsetMin = days * Reminder.DAY_MINUTES,
                    minuteOfDay = Reminder.DEFAULT_MINUTE_OF_DAY,
                )
                DropdownMenuItem(
                    text = { Text(reminderLabel(option, allDay = true)) },
                    onClick = { onChange(days); menu = false },
                )
            }
        }
    }
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
