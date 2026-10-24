package com.meolist.feature.habits

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.ColorPicker
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoIconPicker
import com.meolist.core.ui.MeoPalette
import com.meolist.core.ui.currentLocale
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitInteractor
import com.meolist.domain.habit.HabitRepository
import com.meolist.domain.habit.HabitSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HabitEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val habit: Habit = Habit(name = ""),
    val targetText: String = "1",
    val nameError: Boolean = false,
)

@HiltViewModel
class HabitEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val habits: HabitRepository,
    private val interactor: HabitInteractor,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val habitId: Long = savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0
    private val _state = MutableStateFlow(HabitEditState())
    val state: StateFlow<HabitEditState> = _state

    init {
        viewModelScope.launch {
            val habit = if (habitId == 0L) null else habits.getHabit(habitId)
            _state.value = if (habit == null) {
                HabitEditState(
                    loaded = true,
                    habit = Habit(
                        name = "",
                        icon = "check",
                        color = MeoPalette.colors[1],
                        startDay = clock.today(),
                    ),
                )
            } else {
                HabitEditState(loaded = true, isNew = false, habit = habit, targetText = habit.dailyTarget.toString())
            }
        }
    }

    fun update(transform: (Habit) -> Habit) {
        _state.value = _state.value.let { it.copy(habit = transform(it.habit), nameError = false) }
    }

    fun setScheduleKind(kind: ScheduleKind) {
        update { habit ->
            val schedule = when (kind) {
                ScheduleKind.DAILY -> HabitSchedule.Daily
                ScheduleKind.DAYS -> when (val current = habit.schedule) {
                    is HabitSchedule.Weekdays -> current
                    else -> HabitSchedule.Weekdays(DayOfWeek.entries.toSet())
                }
                ScheduleKind.WEEKLY -> HabitSchedule.WeeklyTimes(
                    (habit.schedule as? HabitSchedule.WeeklyTimes)?.times ?: 3,
                )
            }
            habit.copy(schedule = schedule)
        }
    }

    fun toggleDay(day: DayOfWeek) {
        update { habit ->
            val days = (habit.schedule as? HabitSchedule.Weekdays)?.days ?: return@update habit
            val next = if (day in days) days - day else days + day
            // At least one day keeps the schedule meaningful.
            habit.copy(schedule = HabitSchedule.Weekdays(next.ifEmpty { setOf(day) }))
        }
    }

    fun setTimes(times: Int) {
        update { it.copy(schedule = HabitSchedule.WeeklyTimes(times.coerceIn(1, 7))) }
    }

    fun setTargetText(text: String) {
        _state.value = _state.value.copy(targetText = text.filter(Char::isDigit).take(3))
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.habit.name.isBlank()) {
            _state.value = s.copy(nameError = true)
            return
        }
        val target = s.targetText.toIntOrNull()?.coerceIn(1, 999) ?: 1
        appScope.launch { interactor.save(s.habit.copy(dailyTarget = target)) }
        onSaved()
    }

    fun delete(onDeleted: () -> Unit) {
        if (habitId == 0L) return
        appScope.launch { interactor.delete(habitId) }
        onDeleted()
    }
}

enum class ScheduleKind { DAILY, DAYS, WEEKLY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditRoute(onBack: () -> Unit, vm: HabitEditViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val locale = currentLocale()
    val habit = state.habit
    val color = Color(habit.color)
    val kind = when (habit.schedule) {
        HabitSchedule.Daily -> ScheduleKind.DAILY
        is HabitSchedule.Weekdays -> ScheduleKind.DAYS
        is HabitSchedule.WeeklyTimes -> ScheduleKind.WEEKLY
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.habits_new else R.string.habits_edit)) },
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
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(ActivityIcons.of(habit.icon), null, tint = Color.White)
                }
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = habit.name,
                    onValueChange = { name -> vm.update { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.habits_name)) },
                    isError = state.nameError,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle(stringResource(R.string.habits_schedule))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ScheduleKind.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = kind == item,
                        onClick = { vm.setScheduleKind(item) },
                        shape = SegmentedButtonDefaults.itemShape(index, ScheduleKind.entries.size),
                    ) {
                        Text(stringResource(scheduleKindLabel(item)))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (val schedule = habit.schedule) {
                is HabitSchedule.Weekdays -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in schedule.days,
                            onClick = { vm.toggleDay(day) },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                        )
                    }
                }
                is HabitSchedule.WeeklyTimes -> {
                    Text(
                        stringResource(R.string.habits_times_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..7).forEach { times ->
                            FilterChip(
                                selected = schedule.times == times,
                                onClick = { vm.setTimes(times) },
                                label = { Text(times.toString()) },
                            )
                        }
                    }
                }
                HabitSchedule.Daily -> Unit
            }

            SectionTitle(stringResource(R.string.habits_daily_target))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.targetText,
                    onValueChange = vm::setTargetText,
                    label = { Text(stringResource(R.string.habits_target)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = habit.unit,
                    onValueChange = { unit -> vm.update { it.copy(unit = unit) } },
                    label = { Text(stringResource(R.string.habits_unit)) },
                    placeholder = { Text(stringResource(R.string.habits_unit_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.habits_target_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle(stringResource(R.string.habits_color))
            ColorPicker(selected = habit.color, onSelect = { c -> vm.update { it.copy(color = c) } })
            SectionTitle(stringResource(R.string.habits_icon))
            MeoIconPicker(selected = habit.icon, tint = color, onSelect = { key -> vm.update { it.copy(icon = key) } })

            Button(
                onClick = { vm.save(onBack) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) { Text(stringResource(com.meolist.core.R.string.core_save)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.habits_delete_title, habit.name),
            text = stringResource(R.string.habits_delete_text),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun scheduleKindLabel(kind: ScheduleKind): Int = when (kind) {
    ScheduleKind.DAILY -> R.string.habits_schedule_daily
    ScheduleKind.DAYS -> R.string.habits_schedule_days
    ScheduleKind.WEEKLY -> R.string.habits_schedule_weekly_short
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}
