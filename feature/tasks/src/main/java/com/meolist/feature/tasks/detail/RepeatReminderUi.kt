package com.meolist.feature.tasks.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meolist.core.ui.MeoDatePickerDialog
import com.meolist.core.ui.MeoTimePickerDialog
import com.meolist.core.ui.ReminderPermissionBanner
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.longDate
import com.meolist.core.ui.shortDate
import com.meolist.core.ui.timeLabel
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.reminder.Reminder
import com.meolist.domain.reminder.ReminderKind
import com.meolist.domain.reminder.ReminderOwner
import com.meolist.domain.repeat.Freq
import com.meolist.domain.repeat.RepeatRule
import com.meolist.domain.repeat.WeekdayNum
import com.meolist.domain.task.Task
import com.meolist.feature.tasks.R
import com.meolist.core.ui.rememberReminderPermissionState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

// ---------------------------------------------------------------- Repeat

private val WEEK = DayOfWeek.entries // Monday first

@Composable
@ReadOnlyComposable
private fun weekdayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, currentLocale())

@Composable
@ReadOnlyComposable
private fun ordinalLabel(ordinal: Int): String = stringResource(
    when (ordinal) {
        1 -> R.string.tasks_ordinal_1
        2 -> R.string.tasks_ordinal_2
        3 -> R.string.tasks_ordinal_3
        4 -> R.string.tasks_ordinal_4
        5 -> R.string.tasks_ordinal_5
        else -> R.string.tasks_ordinal_last
    },
)

/** Human readable description of [rule]; implicit days are taken from [anchor] (the due date). */
@Composable
@ReadOnlyComposable
internal fun repeatLabel(rule: RepeatRule, fromCompletion: Boolean, anchor: LocalDate): String {
    val n = rule.interval
    val base = when (rule.freq) {
        Freq.DAILY -> if (n == 1) stringResource(R.string.tasks_repeat_daily)
        else stringResource(R.string.tasks_repeat_every_days, n)
        Freq.WEEKLY -> {
            val days = rule.byDay.map { it.day }.ifEmpty { listOf(anchor.dayOfWeek) }.distinct().sorted()
            when {
                n == 1 && days == WEEK.take(5) -> stringResource(R.string.tasks_repeat_weekdays)
                n == 1 && days == WEEK -> stringResource(R.string.tasks_repeat_daily)
                else -> {
                    val locale = currentLocale()
                    val names = days.joinToString(stringResource(R.string.tasks_list_separator)) {
                        it.getDisplayName(TextStyle.SHORT, locale)
                    }
                    if (n == 1) stringResource(R.string.tasks_repeat_weekly_on, names)
                    else stringResource(R.string.tasks_repeat_every_weeks_on, n, names)
                }
            }
        }
        Freq.MONTHLY -> {
            val nth = rule.byDay.firstOrNull { it.ordinal != 0 }
            val day = when {
                nth != null -> stringResource(R.string.tasks_repeat_on_nth_weekday, ordinalLabel(nth.ordinal), weekdayName(nth.day))
                rule.byMonthDay == listOf(-1) -> stringResource(R.string.tasks_repeat_on_last_day)
                else -> stringResource(R.string.tasks_repeat_on_day, rule.byMonthDay.firstOrNull() ?: anchor.dayOfMonth)
            }
            if (n == 1) stringResource(R.string.tasks_repeat_monthly_on, day)
            else stringResource(R.string.tasks_repeat_every_months_on, n, day)
        }
        Freq.YEARLY -> {
            val date = shortDate(anchor, anchor, currentLocale())
            if (n == 1) stringResource(R.string.tasks_repeat_yearly_on, date)
            else stringResource(R.string.tasks_repeat_every_years_on, n, date)
        }
    }
    val end = when {
        rule.count != null -> pluralStringResource(R.plurals.tasks_repeat_times_left, rule.count!!, rule.count!!)
        rule.until != null -> stringResource(R.string.tasks_repeat_until, shortDate(rule.until!!, anchor, currentLocale()))
        else -> null
    }
    val after = if (fromCompletion) stringResource(R.string.tasks_repeat_after_completion_short) else null
    return listOfNotNull(base, end, after).joinToString(stringResource(R.string.tasks_label_separator))
}

@Composable
internal fun RepeatRow(task: Task, today: LocalDate, dueDate: LocalDate?, editable: Boolean, onSet: (RepeatRule?, Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val anchor = dueDate ?: today
    val rule = RepeatRule.parse(task.repeatRule)
    Box {
        DetailRow(
            icon = Icons.Outlined.Repeat,
            label = stringResource(R.string.tasks_repeat),
            value = rule?.let { repeatLabel(it, task.repeatFromCompletion, anchor) } ?: stringResource(R.string.tasks_none),
            valueColor = if (rule != null) MaterialTheme.colorScheme.primary else null,
            enabled = editable,
            onClick = { menu = true },
        )
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tasks_none)) }, onClick = { onSet(null, false); menu = false })
                val presets = listOf(
                    RepeatRule.daily(),
                    RepeatRule(Freq.WEEKLY),
                    RepeatRule.WEEKDAYS,
                    RepeatRule.monthly(),
                    RepeatRule.yearly(),
                )
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(repeatLabel(preset, false, anchor)) },
                        onClick = { onSet(preset, false); menu = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_repeat_custom)) },
                    onClick = { menu = false; custom = true },
                )
            }
        }
    }
    if (custom) {
        CustomRepeatDialog(
            initial = rule,
            initialFromCompletion = task.repeatFromCompletion,
            anchor = anchor,
            onConfirm = onSet,
            onDismiss = { custom = false },
        )
    }
}

private enum class MonthMode { DAY, NTH_WEEKDAY, LAST_DAY }
private enum class EndMode { NEVER, COUNT, UNTIL }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomRepeatDialog(
    initial: RepeatRule?,
    initialFromCompletion: Boolean,
    anchor: LocalDate,
    onConfirm: (RepeatRule, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var freq by remember { mutableStateOf(initial?.freq ?: Freq.WEEKLY) }
    var interval by remember { mutableIntStateOf(initial?.interval ?: 1) }
    var weekdays by remember {
        mutableStateOf(initial?.byDay?.filter { it.ordinal == 0 }?.map { it.day }?.toSet()?.ifEmpty { null } ?: setOf(anchor.dayOfWeek))
    }
    var monthMode by remember {
        mutableStateOf(
            when {
                initial == null || initial.freq != Freq.MONTHLY -> MonthMode.DAY
                initial.byDay.any { it.ordinal != 0 } -> MonthMode.NTH_WEEKDAY
                initial.byMonthDay == listOf(-1) -> MonthMode.LAST_DAY
                else -> MonthMode.DAY
            },
        )
    }
    var endMode by remember {
        mutableStateOf(
            when {
                initial?.count != null -> EndMode.COUNT
                initial?.until != null -> EndMode.UNTIL
                else -> EndMode.NEVER
            },
        )
    }
    var count by remember { mutableIntStateOf(initial?.count ?: 10) }
    var until by remember { mutableStateOf(initial?.until ?: anchor.plusMonths(1)) }
    var fromCompletion by remember { mutableStateOf(initialFromCompletion) }
    var pickUntil by remember { mutableStateOf(false) }

    val nthOrdinal = ((anchor.dayOfMonth - 1) / 7 + 1).let { if (it >= 5) -1 else it }

    fun build(): RepeatRule {
        val byDay: List<WeekdayNum>
        val byMonthDay: List<Int>
        when (freq) {
            Freq.WEEKLY -> {
                byDay = WEEK.filter { it in weekdays }.map { WeekdayNum(it) }
                byMonthDay = emptyList()
            }
            Freq.MONTHLY -> when (monthMode) {
                MonthMode.DAY -> { byDay = emptyList(); byMonthDay = emptyList() }
                MonthMode.NTH_WEEKDAY -> { byDay = listOf(WeekdayNum(anchor.dayOfWeek, nthOrdinal)); byMonthDay = emptyList() }
                MonthMode.LAST_DAY -> { byDay = emptyList(); byMonthDay = listOf(-1) }
            }
            else -> { byDay = emptyList(); byMonthDay = emptyList() }
        }
        return RepeatRule(
            freq = freq,
            interval = interval,
            byDay = byDay,
            byMonthDay = byMonthDay,
            count = if (endMode == EndMode.COUNT) count else null,
            until = if (endMode == EndMode.UNTIL) until else null,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_repeat_custom)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Freq.DAILY to R.string.tasks_freq_day,
                        Freq.WEEKLY to R.string.tasks_freq_week,
                        Freq.MONTHLY to R.string.tasks_freq_month,
                        Freq.YEARLY to R.string.tasks_freq_year,
                    ).forEach { (f, label) ->
                        FilterChip(selected = freq == f, onClick = { freq = f }, label = { Text(stringResource(label)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tasks_repeat_every), Modifier.weight(1f))
                    Stepper(interval, 1..99) { interval = it }
                }
                when (freq) {
                    Freq.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEK.forEach { day ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = {
                                    val next = if (day in weekdays) weekdays - day else weekdays + day
                                    if (next.isNotEmpty()) weekdays = next
                                },
                                label = { Text(weekdayName(day)) },
                            )
                        }
                    }
                    Freq.MONTHLY -> Column {
                        RadioLine(monthMode == MonthMode.DAY, stringResource(R.string.tasks_repeat_on_day, anchor.dayOfMonth)) {
                            monthMode = MonthMode.DAY
                        }
                        RadioLine(
                            monthMode == MonthMode.NTH_WEEKDAY,
                            stringResource(R.string.tasks_repeat_on_nth_weekday, ordinalLabel(nthOrdinal), weekdayName(anchor.dayOfWeek)),
                        ) { monthMode = MonthMode.NTH_WEEKDAY }
                        RadioLine(monthMode == MonthMode.LAST_DAY, stringResource(R.string.tasks_repeat_on_last_day)) {
                            monthMode = MonthMode.LAST_DAY
                        }
                    }
                    else -> Unit
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.tasks_repeat_ends), style = MaterialTheme.typography.titleSmall)
                RadioLine(endMode == EndMode.NEVER, stringResource(R.string.tasks_repeat_never_ends)) { endMode = EndMode.NEVER }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioLine(endMode == EndMode.COUNT, stringResource(R.string.tasks_repeat_after_count), Modifier.weight(1f)) {
                        endMode = EndMode.COUNT
                    }
                    if (endMode == EndMode.COUNT) Stepper(count, 1..999) { count = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioLine(endMode == EndMode.UNTIL, stringResource(R.string.tasks_repeat_on_date), Modifier.weight(1f)) {
                        endMode = EndMode.UNTIL
                    }
                    if (endMode == EndMode.UNTIL) {
                        TextButton(onClick = { pickUntil = true }) { Text(longDate(until, currentLocale())) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { fromCompletion = !fromCompletion },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tasks_repeat_from_completion))
                        Text(
                            stringResource(R.string.tasks_repeat_from_completion_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = fromCompletion, onCheckedChange = { fromCompletion = it })
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    repeatLabel(build(), fromCompletion, anchor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(build(), fromCompletion); onDismiss() }) {
                Text(stringResource(com.meolist.core.R.string.core_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
        },
    )
    if (pickUntil) {
        MeoDatePickerDialog(initial = until, onConfirm = { until = maxOf(it, anchor) }, onDismiss = { pickUntil = false })
    }
}

@Composable
private fun RadioLine(selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Text(label)
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Outlined.Remove, null)
        }
        Text(value.toString(), Modifier.widthIn(min = 32.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Outlined.Add, null)
        }
    }
}

// ---------------------------------------------------------------- Reminders

private const val DAY = Reminder.DAY_MINUTES

private fun timedPresets(taskId: Long) = listOf(0, 5, 30, 60, DAY).map {
    Reminder(ownerType = ReminderOwner.TASK, ownerId = taskId, offsetMin = it)
}

private fun allDayPresets(taskId: Long) = listOf(0, DAY, 2 * DAY, 7 * DAY).map {
    Reminder(ownerType = ReminderOwner.TASK, ownerId = taskId, offsetMin = it, minuteOfDay = Reminder.DEFAULT_MINUTE_OF_DAY)
}

@Composable
@ReadOnlyComposable
private fun durationText(minutes: Int): String = when {
    minutes % DAY == 0 -> pluralStringResource(R.plurals.tasks_days, minutes / DAY, minutes / DAY)
    minutes % 60 == 0 -> pluralStringResource(R.plurals.tasks_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.tasks_minutes, minutes, minutes)
}

@Composable
@ReadOnlyComposable
internal fun reminderLabel(reminder: Reminder, allDay: Boolean): String {
    if (!allDay) {
        return if (reminder.offsetMin == 0) stringResource(R.string.tasks_reminder_on_time)
        else stringResource(R.string.tasks_reminder_before, durationText(reminder.offsetMin))
    }
    val minute = reminder.minuteOfDay ?: Reminder.DEFAULT_MINUTE_OF_DAY
    val time = timeLabel(LocalTime.of(minute / 60, minute % 60))
    val days = reminder.offsetMin / DAY
    return if (days == 0) stringResource(R.string.tasks_reminder_same_day, time)
    else stringResource(R.string.tasks_reminder_days_before, pluralStringResource(R.plurals.tasks_days, days, days), time)
}

@Composable
internal fun ReminderRow(task: Task, reminders: List<Reminder>, editable: Boolean, onSet: (List<Reminder>) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    val permissions = rememberReminderPermissionState()
    val hasDue = task.dueAt != null
    val active = reminders.filter { it.enabled }
    DetailRow(
        icon = Icons.Outlined.Alarm,
        label = stringResource(R.string.tasks_reminders),
        value = when {
            !hasDue -> stringResource(R.string.tasks_reminder_needs_due)
            active.isEmpty() -> stringResource(R.string.tasks_none)
            active.size == 1 -> reminderLabel(active.first(), task.allDay)
            else -> stringResource(R.string.tasks_reminder_count, active.size)
        },
        valueColor = if (hasDue && active.isNotEmpty()) MaterialTheme.colorScheme.primary else null,
        enabled = editable && hasDue,
        onClick = { dialog = true },
    )
    if (editable && hasDue && active.isNotEmpty() && !task.isDone) {
        ReminderPermissionBanner(permissions, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    if (dialog) {
        ReminderDialog(
            task = task,
            initial = active,
            onConfirm = { chosen ->
                onSet(chosen)
                if (chosen.isNotEmpty()) permissions.requestNotificationsIfNeeded()
            },
            onDismiss = { dialog = false },
        )
    }
}

@Composable
internal fun ReminderOptionRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        } else if (selected) {
            Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(task: Task, initial: List<Reminder>, onConfirm: (List<Reminder>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presets = if (task.allDay) allDayPresets(task.id) else timedPresets(task.id)
    // Options = presets plus any custom reminders already set, in a stable order.
    var options by remember {
        mutableStateOf((presets + initial.filter { r -> presets.none { it.sameSetting(r) } }).sortedWith(reminderOrder))
    }
    var selected by remember { mutableStateOf(options.filter { o -> initial.any { it.sameSetting(o) } }) }
    var customOpen by remember { mutableStateOf(false) }
    var customDays by remember { mutableIntStateOf(0) }
    var customMinute by remember { mutableIntStateOf(Reminder.DEFAULT_MINUTE_OF_DAY) }
    var customAmount by remember { mutableStateOf("10") }
    var customUnit by remember { mutableStateOf(OffsetUnit.MINUTES) }
    var pickTime by remember { mutableStateOf(false) }
    val amount = customAmount.toIntOrNull()
    val customReminder = if (task.allDay) {
        Reminder(
            ownerType = ReminderOwner.TASK,
            ownerId = task.id,
            kind = ReminderKind.RELATIVE,
            offsetMin = customDays * DAY,
            minuteOfDay = customMinute,
        )
    } else {
        amount?.takeIf { it in 0..999 }?.let {
            Reminder(ownerType = ReminderOwner.TASK, ownerId = task.id, offsetMin = it * customUnit.minutes)
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
            Spacer(Modifier.height(8.dp))
            ReminderOptionRow(
                icon = Icons.Outlined.NotificationsOff,
                label = stringResource(R.string.tasks_reminder_off),
                selected = selected.isEmpty(),
                onClick = { selected = emptyList() },
            )
            options.forEach { option ->
                val checked = selected.any { it.sameSetting(option) }
                ReminderOptionRow(
                    icon = Icons.Outlined.Alarm,
                    label = reminderLabel(option, task.allDay),
                    selected = checked,
                    onClick = {
                        selected = if (checked) {
                            selected.filterNot { it.sameSetting(option) }
                        } else {
                            selected + option
                        }
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
                        if (task.allDay) {
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
                                    Icons.Outlined.KeyboardArrowRight,
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
                                OffsetUnit.entries.forEach { u ->
                                    FilterChip(
                                        selected = customUnit == u,
                                        onClick = { customUnit = u },
                                        label = { Text(stringResource(u.label)) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                customReminder?.let {
                    Text(
                        reminderLabel(it, task.allDay),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        customReminder?.let { reminder ->
                            if (options.none { it.sameSetting(reminder) }) {
                                options = (options + reminder).sortedWith(reminderOrder)
                            }
                            if (selected.none { it.sameSetting(reminder) }) selected = selected + reminder
                            customOpen = false
                        }
                    },
                    enabled = customReminder != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.tasks_reminder_add))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(com.meolist.core.R.string.core_cancel))
                }
                Button(
                    onClick = {
                        onConfirm(selected.sortedWith(reminderOrder))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
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

private val reminderOrder = compareBy<Reminder>({ it.offsetMin }, { -(it.minuteOfDay ?: 0) })

internal enum class OffsetUnit(val minutes: Int, val label: Int) {
    MINUTES(1, R.string.tasks_unit_minutes),
    HOURS(60, R.string.tasks_unit_hours),
    DAYS(DAY, R.string.tasks_unit_days),
}

/** The due date of [task] in [zone], used as the anchor for repeat labels. */
internal fun Task.dueDate(zone: java.time.ZoneId): LocalDate? = dueAt?.toLocalDateTime(zone)?.toLocalDate()
