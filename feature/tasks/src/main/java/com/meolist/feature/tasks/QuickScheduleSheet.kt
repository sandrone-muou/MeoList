package com.meolist.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meolist.core.ui.MeoTimePickerDialog
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.relativeDateLabel
import com.meolist.core.ui.timeLabel
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.feature.tasks.detail.ReminderOptionRow
import com.meolist.feature.tasks.detail.OffsetUnit
import com.meolist.feature.tasks.detail.reminderLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private enum class ScheduleTab { DATE, TIME }

private val dayFormatter = TextStyle.NARROW
private val afternoonTime = LocalTime.of(14, 0)

private fun nextMonday(today: LocalDate): LocalDate {
    val days = (DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7
    return today.plusDays((if (days == 0) 7 else days).toLong())
}

/**
 * Date/time editor modelled on the reference sheets: quick dates, a month calendar and the
 * time / reminder rows live in one bottom sheet instead of separate pickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickScheduleSheet(
    initialDate: LocalDate?,
    initialTime: LocalTime?,
    initialReminder: Reminder?,
    today: LocalDate,
    onConfirm: (LocalDate?, LocalTime?, Reminder?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(initialTime) }
    var reminder by remember { mutableStateOf(initialReminder) }
    var tab by remember { mutableStateOf(ScheduleTab.DATE) }
    var month by remember { mutableStateOf(YearMonth.from(initialDate ?: today)) }
    var pickTime by remember { mutableStateOf(false) }
    var editReminder by remember { mutableStateOf(false) }

    fun setDate(next: LocalDate?) {
        date = next
        if (next == null) {
            time = null
            reminder = null
        } else {
            month = YearMonth.from(next)
        }
    }

    fun setAllDay(allDay: Boolean) {
        val next = if (allDay) null else time ?: LocalTime.of(9, 0)
        if ((next == null) != (time == null)) reminder = null
        time = next
    }

    fun setQuickDate(next: LocalDate) {
        if (time != null) reminder = null
        date = next
        time = null
        month = YearMonth.from(next)
    }

    fun setAfternoon() {
        if (time == null) reminder = null
        date = today
        time = afternoonTime
        month = YearMonth.from(today)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(com.meolist.core.R.string.core_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                ScheduleTabLabel(
                    label = stringResource(R.string.schedule_tab_date),
                    selected = tab == ScheduleTab.DATE,
                    onClick = { tab = ScheduleTab.DATE },
                )
                Spacer(Modifier.width(16.dp))
                ScheduleTabLabel(
                    label = stringResource(R.string.schedule_tab_time),
                    selected = tab == ScheduleTab.TIME,
                    onClick = { tab = ScheduleTab.TIME },
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        onConfirm(date, time, reminder.takeIf { date != null })
                        onDismiss()
                    },
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        stringResource(com.meolist.core.R.string.core_ok),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            ) {
                when (tab) {
                    ScheduleTab.DATE -> DateTab(
                        date = date,
                        time = time,
                        reminder = reminder,
                        today = today,
                        month = month,
                        onMonthChange = { month = it },
                        onSelectDate = ::setDate,
                        onQuickDate = ::setQuickDate,
                        onSetAfternoon = ::setAfternoon,
                        onEditTime = { pickTime = true },
                        onEditReminder = { editReminder = true },
                        onClearReminder = { reminder = null },
                    )
                    ScheduleTab.TIME -> TimeTab(
                        date = date,
                        time = time,
                        reminder = reminder,
                        today = today,
                        onOpenDateTab = { tab = ScheduleTab.DATE },
                        onPickTime = { pickTime = true },
                        onAllDayChange = ::setAllDay,
                        onEditReminder = { editReminder = true },
                        onClearReminder = { reminder = null },
                    )
                }
            }
        }
    }

    if (pickTime) {
        MeoTimePickerDialog(
            initial = time ?: LocalTime.of(9, 0),
            onConfirm = { picked ->
                if (time == null) reminder = null
                time = picked
            },
            onDismiss = { pickTime = false },
            onClear = {
                if (time != null) reminder = null
                time = null
            },
        )
    }
    val rescheduleDate = date
    if (editReminder && rescheduleDate != null) {
        QuickReminderSheet(
            allDay = time == null,
            dueDate = rescheduleDate,
            dueTime = time,
            today = today,
            initial = reminder,
            onConfirm = { reminder = it },
            onDismiss = { editReminder = false },
        )
    }
}

@Composable
private fun DateTab(
    date: LocalDate?,
    time: LocalTime?,
    reminder: Reminder?,
    today: LocalDate,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate?) -> Unit,
    onQuickDate: (LocalDate) -> Unit,
    onSetAfternoon: () -> Unit,
    onEditTime: () -> Unit,
    onEditReminder: () -> Unit,
    onClearReminder: () -> Unit,
) {
    val monday = nextMonday(today)
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickDateTile(
                icon = Icons.Outlined.Today,
                label = stringResource(com.meolist.core.R.string.core_today),
                selected = date == today && time == null,
                modifier = Modifier.weight(1f),
                onClick = { onQuickDate(today) },
            )
            QuickDateTile(
                icon = Icons.Outlined.WbSunny,
                label = stringResource(com.meolist.core.R.string.core_tomorrow),
                selected = date == today.plusDays(1),
                modifier = Modifier.weight(1f),
                onClick = { onQuickDate(today.plusDays(1)) },
            )
            QuickDateTile(
                icon = Icons.Outlined.DateRange,
                label = stringResource(R.string.schedule_next_monday),
                selected = date == monday,
                modifier = Modifier.weight(1f),
                onClick = { onQuickDate(monday) },
            )
            QuickDateTile(
                icon = Icons.Outlined.LightMode,
                label = stringResource(R.string.schedule_this_afternoon),
                selected = date == today && time == afternoonTime,
                modifier = Modifier.weight(1f),
                onClick = onSetAfternoon,
            )
        }
        Spacer(Modifier.height(16.dp))
        MonthCalendar(
            month = month,
            today = today,
            selected = date,
            onMonthChange = onMonthChange,
            onSelect = onSelectDate,
        )
        if (date != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButtonText(
                    text = stringResource(R.string.schedule_no_date),
                    onClick = { onSelectDate(null) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ScheduleRowsCard(
            date = date,
            time = time,
            reminder = reminder,
            onEditTime = onEditTime,
            onEditReminder = onEditReminder,
            onClearReminder = onClearReminder,
        )
    }
}

@Composable
private fun TimeTab(
    date: LocalDate?,
    time: LocalTime?,
    reminder: Reminder?,
    today: LocalDate,
    onOpenDateTab: () -> Unit,
    onPickTime: () -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onEditReminder: () -> Unit,
    onClearReminder: () -> Unit,
) {
    val locale = currentLocale()
    val dateText = date?.format(
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日，EEE" else "EEE, MMM d", locale),
    ) ?: stringResource(R.string.schedule_no_date)
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScheduleValueCard(
                label = stringResource(R.string.schedule_tab_date),
                value = dateText,
                caption = date?.let { relativeDateLabel(it, today) },
                modifier = Modifier.weight(1f),
                onClick = onOpenDateTab,
            )
            ScheduleValueCard(
                label = stringResource(R.string.tasks_reminder_at_time),
                value = time?.let { timeLabel(it) } ?: stringResource(R.string.schedule_all_day),
                caption = if (time == null) {
                    stringResource(R.string.schedule_all_day_hint)
                } else {
                    stringResource(R.string.schedule_due_time_hint)
                },
                modifier = Modifier.weight(1f),
                onClick = onPickTime,
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.schedule_all_day),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = time == null, onCheckedChange = onAllDayChange)
            }
        }
        Spacer(Modifier.height(12.dp))
        ScheduleRowsCard(
            date = date,
            time = time,
            reminder = reminder,
            showTimeRow = false,
            onEditTime = onPickTime,
            onEditReminder = onEditReminder,
            onClearReminder = onClearReminder,
        )
    }
}

@Composable
private fun ScheduleRowsCard(
    date: LocalDate?,
    time: LocalTime?,
    reminder: Reminder?,
    onEditTime: () -> Unit,
    onEditReminder: () -> Unit,
    onClearReminder: () -> Unit,
    showTimeRow: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (showTimeRow) {
                ScheduleRow(
                    icon = Icons.Outlined.Schedule,
                    label = stringResource(R.string.tasks_reminder_at_time),
                    value = time?.let { timeLabel(it) } ?: stringResource(R.string.tasks_none),
                    onClick = onEditTime,
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            ScheduleRow(
                icon = Icons.Outlined.Alarm,
                label = stringResource(R.string.tasks_reminders),
                value = when {
                    date == null -> stringResource(R.string.tasks_reminder_needs_due)
                    reminder == null -> stringResource(R.string.tasks_none)
                    else -> reminderLabel(reminder, allDay = time == null)
                },
                enabled = date != null,
                valueColor = if (date != null && reminder != null) MaterialTheme.colorScheme.primary else null,
                onClick = onEditReminder,
                onClear = if (reminder != null) onClearReminder else null,
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean = true,
    valueColor: Color? = null,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Outlined.Close,
                    stringResource(com.meolist.core.R.string.core_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickReminderSheet(
    allDay: Boolean,
    dueDate: LocalDate,
    dueTime: LocalTime?,
    today: LocalDate,
    initial: Reminder?,
    onConfirm: (Reminder?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customOpen by remember { mutableStateOf(false) }
    var customDays by remember {
        mutableIntStateOf((initial?.offsetMin ?: 0) / Reminder.DAY_MINUTES)
    }
    var customMinute by remember {
        mutableIntStateOf(initial?.minuteOfDay ?: Reminder.DEFAULT_MINUTE_OF_DAY)
    }
    var customAmount by remember { mutableStateOf("10") }
    var customUnit by remember { mutableStateOf(OffsetUnit.MINUTES) }
    var pickTime by remember { mutableStateOf(false) }

    val presets = if (allDay) {
        listOf(0, 1, 3).map { days ->
            Reminder(
                ownerType = ReminderOwner.TASK,
                ownerId = 0,
                offsetMin = days * Reminder.DAY_MINUTES,
                minuteOfDay = Reminder.DEFAULT_MINUTE_OF_DAY,
            )
        }
    } else {
        listOf(0, 5, 30, 60, Reminder.DAY_MINUTES).map { minutes ->
            Reminder(ownerType = ReminderOwner.TASK, ownerId = 0, offsetMin = minutes)
        }
    }
    fun isPreset(reminder: Reminder?) = reminder != null && presets.any { it.sameSetting(reminder) }
    val initialIsPreset = isPreset(initial)
    val amount = customAmount.toIntOrNull()
    val customReminder = if (allDay) {
        Reminder(
            ownerType = ReminderOwner.TASK,
            ownerId = 0,
            offsetMin = customDays * Reminder.DAY_MINUTES,
            minuteOfDay = customMinute,
        )
    } else {
        amount?.takeIf { it in 0..999 }?.let {
            Reminder(ownerType = ReminderOwner.TASK, ownerId = 0, offsetMin = it * customUnit.minutes)
        }
    }

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
                    stringResource(R.string.tasks_reminders),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(com.meolist.core.R.string.core_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                if (allDay) {
                    stringResource(
                        R.string.tasks_reminder_due_context,
                        relativeDateLabel(dueDate, today),
                    )
                } else {
                    stringResource(
                        R.string.tasks_reminder_due_context_timed,
                        relativeDateLabel(dueDate, today),
                        timeLabel(dueTime ?: LocalTime.of(9, 0)),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ReminderOptionRow(
                icon = Icons.Outlined.NotificationsOff,
                label = stringResource(R.string.tasks_reminder_off),
                selected = initial == null,
                onClick = {
                    onConfirm(null)
                    onDismiss()
                },
            )
            presets.forEach { option ->
                ReminderOptionRow(
                    icon = Icons.Outlined.Alarm,
                    label = reminderLabel(option, allDay = allDay),
                    selected = initialIsPreset && initial?.sameSetting(option) == true,
                    onClick = {
                        onConfirm(option)
                        onDismiss()
                    },
                )
            }
            if (initial != null && !initialIsPreset) {
                ReminderOptionRow(
                    icon = Icons.Outlined.Alarm,
                    label = reminderLabel(initial, allDay = allDay),
                    selected = true,
                    onClick = {
                        onConfirm(initial)
                        onDismiss()
                    },
                )
            }
            ReminderOptionRow(
                icon = Icons.Outlined.Tune,
                label = stringResource(R.string.tasks_reminder_custom),
                selected = customOpen,
                onClick = { customOpen = !customOpen },
                trailing = {
                    Icon(
                        if (customOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (customOpen) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (allDay) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.tasks_reminder_days_early),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { if (customDays > 0) customDays-- },
                                    enabled = customDays > 0,
                                ) {
                                    Icon(Icons.Outlined.Remove, null)
                                }
                                Text(customDays.toString(), style = MaterialTheme.typography.titleMedium)
                                IconButton(
                                    onClick = { if (customDays < 60) customDays++ },
                                    enabled = customDays < 60,
                                ) {
                                    Icon(Icons.Outlined.Add, null)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { pickTime = true }
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(
                                    stringResource(R.string.tasks_reminder_at_time),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    timeLabel(LocalTime.of(customMinute / 60, customMinute % 60)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = customAmount,
                                onValueChange = { text -> customAmount = text.filter { it.isDigit() }.take(3) },
                                singleLine = true,
                                isError = amount == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text(stringResource(R.string.tasks_reminder_how_early)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                OffsetUnit.entries.forEach { unit ->
                                    FilterChip(
                                        selected = customUnit == unit,
                                        onClick = { customUnit = unit },
                                        label = { Text(stringResource(unit.label)) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                customReminder?.let {
                    Text(
                        reminderLabel(it, allDay = allDay),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        customReminder?.let {
                            onConfirm(it)
                            onDismiss()
                        }
                    },
                    enabled = customReminder != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(com.meolist.core.R.string.core_ok))
                }
            }
        }
    }
    if (pickTime) {
        MeoTimePickerDialog(
            initial = LocalTime.of(customMinute / 60, customMinute % 60),
            onConfirm = {
                customMinute = it.hour * 60 + it.minute
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }
}

@Composable
private fun QuickDateTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = currentLocale()
    val title = month.atDay(1).format(
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy", locale),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            (0 until 7).forEach { i ->
                val day = DayOfWeek.of((DayOfWeek.MONDAY.value - 1 + i) % 7 + 1)
                Text(
                    day.getDisplayName(dayFormatter, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val daysInMonth = month.lengthOfMonth()
        val trailing = (7 - (leading + daysInMonth) % 7) % 7
        val cells = buildList {
            for (i in leading downTo 1) add(first.minusDays(i.toLong()))
            for (day in 1..daysInMonth) add(month.atDay(day))
            for (i in 1..trailing) add(month.atEndOfMonth().plusDays(i.toLong()))
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == month
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable(enabled = inMonth) { onSelect(date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (date == selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    date == selected -> MaterialTheme.colorScheme.onPrimary
                                    date == today -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleValueCard(
    label: String,
    value: String,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            caption?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ScheduleTabLabel(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(24.dp)
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}

@Composable
private fun TextButtonText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
