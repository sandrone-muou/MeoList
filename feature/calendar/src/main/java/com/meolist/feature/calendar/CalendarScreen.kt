package com.meolist.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.theme.Meo
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.accentColor
import com.meolist.core.ui.components.CalendarNavigator
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.components.MonthDateGrid
import com.meolist.core.ui.components.WeekDateStrip
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.durationText
import com.meolist.core.ui.longDate
import com.meolist.core.ui.readableOn
import com.meolist.core.ui.timeLabel
import com.meolist.domain.calendar.CalendarDay
import com.meolist.domain.calendar.CalendarPeriod
import com.meolist.domain.calendar.CalendarRules
import com.meolist.domain.calendar.TaskCalendarRules
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.today
import com.meolist.domain.countdown.Countdown
import com.meolist.domain.countdown.CountdownMath
import com.meolist.domain.countdown.CountdownMode
import com.meolist.domain.countdown.CountdownRepository
import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitInteractor
import com.meolist.domain.habit.HabitRepository
import com.meolist.domain.habit.HabitSchedule
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.TimeRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

typealias CalendarMode = CalendarPeriod

private data class CalendarInput(val mode: CalendarMode, val anchor: LocalDate, val selected: LocalDate)

data class CalendarState(
    val mode: CalendarMode = CalendarMode.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val selected: LocalDate = LocalDate.now(),
    val days: List<CalendarDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val types: List<ActivityType> = emptyList(),
    val loaded: Boolean = false,
) {
    val selectedDay: CalendarDay? get() = days.firstOrNull { it.date == selected }
    val month: YearMonth get() = YearMonth.from(anchor)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val taskInteractor: TaskInteractor,
    private val habits: HabitRepository,
    private val habitInteractor: HabitInteractor,
    private val countdowns: CountdownRepository,
    private val records: TimeRecordRepository,
    private val types: ActivityTypeRepository,
    prefs: AppPreferences,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val input = MutableStateFlow(
        CalendarInput(
            savedState.get<String>("mode")?.let(CalendarMode::valueOf) ?: CalendarMode.MONTH,
            savedState.get<Long>("anchor")?.let(LocalDate::ofEpochDay) ?: clock.today(),
            savedState.get<Long>("selected")?.let(LocalDate::ofEpochDay) ?: clock.today(),
        ),
    )

    private data class DayData(
        val input: CalendarInput,
        val days: List<CalendarDay>,
        val firstDayOfWeek: DayOfWeek,
        val types: List<ActivityType>,
    )

    private val dayData = combine(input, prefs.settings) { i, settings -> i to settings.firstDayOfWeek }
        .distinctUntilChanged()
        .flatMapLatest { (i, firstDay) ->
            val dates = when (i.mode) {
                CalendarMode.MONTH -> CalendarRules.monthGrid(YearMonth.from(i.anchor), firstDay)
                CalendarMode.WEEK -> CalendarRules.weekDays(i.anchor, firstDay)
            }
            val zone = clock.zone()
            val from = dates.first().startOfDayMillis(zone)
            val to = dates.last().plusDays(1).startOfDayMillis(zone)
            combine(
                tasks.observeAll(),
                habits.observeHabits(),
                habits.observeCheckIns(),
                countdowns.observeAll(),
                records.observeRecords(from, to),
            ) { allTasks, allHabits, checkIns, allCountdowns, allRecords ->
                val byDate = CalendarRules.build(
                    dates, allTasks, allHabits, checkIns, allCountdowns, allRecords, zone,
                )
                DayData(
                    input = i,
                    days = dates.mapNotNull { byDate[it] },
                    firstDayOfWeek = firstDay,
                    types = emptyList(),
                )
            }.combine(types.observeTypes()) { data, allTypes -> data.copy(types = allTypes) }
        }

    val state: StateFlow<CalendarState> = dayData.combine(input) { data, i ->
        CalendarState(
            mode = data.input.mode,
            anchor = data.input.anchor,
            selected = i.selected,
            days = data.days,
            today = clock.today(),
            firstDayOfWeek = data.firstDayOfWeek,
            types = data.types,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    private fun update(next: CalendarInput) {
        savedState["mode"] = next.mode.name
        savedState["anchor"] = next.anchor.toEpochDay()
        savedState["selected"] = next.selected.toEpochDay()
        input.value = next
    }

    fun setMode(mode: CalendarMode) {
        val current = input.value
        update(current.copy(mode = mode, anchor = current.selected))
    }

    fun move(steps: Long) {
        val current = input.value
        val date = TaskCalendarRules.move(current.selected, current.mode, steps)
        update(current.copy(anchor = date, selected = date))
    }

    fun goToday() = select(clock.today())

    fun select(day: LocalDate) {
        update(input.value.copy(anchor = day, selected = day))
    }

    fun toggleTask(task: Task) = appScope.launch {
        taskInteractor.setDone(task.id, !task.isDone)
    }

    fun toggleHabit(habitId: Long, day: LocalDate) = appScope.launch {
        habitInteractor.toggle(habitId, day)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarRoute(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenCountdown: (Long) -> Unit,
    onCreateTask: (LocalDate) -> Unit,
    vm: CalendarViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    MeoScaffold(
        title = stringResource(R.string.calendar_title),
        onBack = onBack,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Meo.spacing.screen),
        ) {
            CalendarNavigator(state.anchor, state.mode, state.firstDayOfWeek, vm::setMode, vm::move, vm::goToday)

            if (state.mode == CalendarMode.MONTH) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        MonthGrid(state, onSelect = vm::select)
                        Legend(Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp))
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        WeekChart(state, onSelect = vm::select)
                        Legend(Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp))
                    }
                }
            }

            val day = state.selectedDay
            if (day != null) {
                Agenda(
                    day = day,
                    types = state.types,
                    today = state.today,
                    onOpenTask = onOpenTask,
                    onToggleTask = vm::toggleTask,
                    onOpenHabit = onOpenHabit,
                    onToggleHabit = { habit -> vm.toggleHabit(habit.id, day.date) },
                    onOpenCountdown = onOpenCountdown,
                    onCreateTask = { onCreateTask(day.date) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarState, onSelect: (LocalDate) -> Unit) {
    val byDate = state.days.associateBy { it.date }
    MonthDateGrid(state.month, state.firstDayOfWeek, state.today, state.selected, onSelect) { date ->
        val day = byDate[date]
        Row(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.xs)) {
            if (day?.tasks?.isNotEmpty() == true) SmallDot(MaterialTheme.colorScheme.primary)
            if (day?.habits?.isNotEmpty() == true) SmallDot(MaterialTheme.colorScheme.tertiary)
            if (day?.countdowns?.isNotEmpty() == true) SmallDot(MaterialTheme.colorScheme.secondary)
            if ((day?.trackedMs ?: 0) > 0) SmallDot(MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SmallDot(color: Color) {
    Box(Modifier.size(3.dp).clip(CircleShape).background(color))
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(4.dp).clip(CircleShape).background(color))
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            MaterialTheme.colorScheme.primary to R.string.calendar_legend_task,
            MaterialTheme.colorScheme.tertiary to R.string.calendar_legend_habit,
            MaterialTheme.colorScheme.secondary to R.string.calendar_legend_countdown,
            MaterialTheme.colorScheme.outline to R.string.calendar_legend_time,
        ).forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeekChart(state: CalendarState, onSelect: (LocalDate) -> Unit) {
    val axisWidth = 22.dp
    val chartHeight = Meo.layout.weekChartHeight
    val typeColors = state.types.associate { it.id to accentColor(it.color) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(axisWidth))
            WeekDateStrip(state.anchor, state.firstDayOfWeek, state.today, state.selected, onSelect, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            TimeAxis(Modifier.width(axisWidth).height(chartHeight))
            state.days.forEach { day ->
                Lane(
                    day = day,
                    today = state.today,
                    selected = day.date == state.selected,
                    typeColors = typeColors,
                    onSelect = { onSelect(day.date) },
                    modifier = Modifier.weight(1f).height(chartHeight),
                )
            }
        }
    }
}

@Composable
private fun TimeAxis(modifier: Modifier = Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    androidx.compose.foundation.Canvas(modifier) {
        listOf(0, 6, 12, 18).forEach { hour ->
            val y = hour / 24f * size.height
            val text = measurer.measure(hour.toString(), style)
            drawText(text, topLeft = Offset(0f, (y - text.size.height / 2f).coerceAtLeast(0f)))
        }
    }
}

/** Draws the 24-hour lane for one day with records as coloured blocks. */
@Composable
private fun Lane(
    day: CalendarDay,
    today: LocalDate,
    selected: Boolean,
    typeColors: Map<Long, Color>,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val nowColor = MaterialTheme.colorScheme.error
    val zone = java.time.ZoneId.systemDefault()
    androidx.compose.foundation.Canvas(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .pointerInput(day) { detectTapGestures { onSelect() } },
    ) {
        for (h in listOf(6, 12, 18)) {
            val y = h / 24f * size.height
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        day.records.forEach { record ->
            val startTime = record.startAt.toLocalDateTime(zone)
            val endTime = record.endAt.toLocalDateTime(zone)
            val startMin = if (startTime.toLocalDate() < day.date) 0f else startTime.hour * 60f + startTime.minute
            val endMin = if (endTime.toLocalDate() > day.date) 1440f else endTime.hour * 60f + endTime.minute
            val y0 = (startMin / 1440f).coerceIn(0f, 1f) * size.height
            val y1 = (endMin / 1440f).coerceIn(0f, 1f) * size.height
            val color = typeColors[record.typeId] ?: Color.Gray
            drawRect(color, Offset(2f, y0), Size(size.width - 4f, (y1 - y0).coerceAtLeast(2f)))
        }
        if (day.date == today) {
            val now = java.time.LocalTime.now()
            val y = (now.hour * 60f + now.minute) / 1440f * size.height
            drawLine(nowColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
    }
}

@Composable
private fun Agenda(
    day: CalendarDay,
    types: List<ActivityType>,
    today: LocalDate,
    onOpenTask: (Long) -> Unit,
    onToggleTask: (Task) -> Unit,
    onOpenHabit: (Long) -> Unit,
    onToggleHabit: (Habit) -> Unit,
    onOpenCountdown: (Long) -> Unit,
    onCreateTask: () -> Unit,
) {
    val locale = currentLocale()
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                longDate(day.date, locale),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCreateTask) {
                Icon(Icons.Outlined.Add, stringResource(R.string.calendar_add_task))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (day.date == today) {
                Text(
                    stringResource(com.meolist.core.R.string.core_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            if (day.trackedMs > 0) {
                Icon(
                    Icons.Outlined.Timer,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    durationText(day.trackedMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (day.isEmpty) {
            Text(
                stringResource(R.string.calendar_nothing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@Column
        }

        if (day.tasks.isNotEmpty()) {
            AgendaSection(
                title = stringResource(R.string.calendar_tasks),
                count = day.tasks.size,
                icon = Icons.Outlined.CheckCircle,
            ) {
                day.tasks.forEachIndexed { index, task ->
                    if (index > 0) AgendaDivider()
                    TaskAgendaRow(
                        task = task,
                        onOpen = { onOpenTask(task.id) },
                        onToggle = { onToggleTask(task) },
                    )
                }
            }
        }

        if (day.habits.isNotEmpty()) {
            AgendaSection(
                title = stringResource(R.string.calendar_habits),
                count = day.habits.size,
                icon = Icons.Outlined.Repeat,
            ) {
                day.habits.forEachIndexed { index, habit ->
                    if (index > 0) AgendaDivider()
                    HabitAgendaRow(
                        habit = habit,
                        done = habit.id in day.doneHabitIds,
                        onOpen = { onOpenHabit(habit.id) },
                        onToggle = { onToggleHabit(habit) },
                    )
                }
            }
        }

        if (day.countdowns.isNotEmpty()) {
            AgendaSection(
                title = stringResource(R.string.calendar_countdowns),
                count = day.countdowns.size,
                icon = Icons.Outlined.Celebration,
            ) {
                day.countdowns.forEachIndexed { index, countdown ->
                    if (index > 0) AgendaDivider()
                    CountdownAgendaRow(
                        countdown = countdown,
                        today = today,
                        onOpen = { onOpenCountdown(countdown.id) },
                    )
                }
            }
        }

        if (day.records.isNotEmpty()) {
            AgendaSection(
                title = stringResource(R.string.calendar_time),
                count = day.records.size,
                icon = Icons.Outlined.Timer,
            ) {
                val typeMap = types.associateBy { it.id }
                day.records.forEachIndexed { index, record ->
                    if (index > 0) AgendaDivider()
                    RecordAgendaRow(record = record, type = typeMap[record.typeId])
                }
            }
        }
    }
}

@Composable
private fun AgendaSection(
    title: String,
    count: Int,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), content = content)
    }
}

@Composable
private fun AgendaDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 40.dp, end = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun TaskAgendaRow(task: Task, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .padding(vertical = 2.dp),
    ) {
        Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
        Text(
            task.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        task.dueAt?.takeIf { !task.allDay }?.let { due ->
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.Schedule,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                timeLabel(due.toLocalDateTime(java.time.ZoneId.systemDefault()).toLocalTime()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun HabitAgendaRow(habit: Habit, done: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val accent = accentColor(habit.color)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ActivityIcons.of(habit.icon), null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                habitScheduleLabel(habit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(
            onClick = onToggle,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (done) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Outlined.Check,
                stringResource(if (done) R.string.calendar_undo_check else R.string.calendar_check),
                tint = if (done) accent.readableOn() else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CountdownAgendaRow(countdown: Countdown, today: LocalDate, onOpen: () -> Unit) {
    val accent = if (countdown.color == 0) MaterialTheme.colorScheme.primary else Color(countdown.color)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Celebration, null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(countdown.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                countdownRelative(countdown, today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (countdown.yearly) {
            Text(
                stringResource(R.string.calendar_yearly),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordAgendaRow(record: com.meolist.domain.tracker.TimeRecord, type: ActivityType?) {
    val accent = type?.let { accentColor(it.color) } ?: MaterialTheme.colorScheme.outline
    val zone = java.time.ZoneId.systemDefault()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ActivityIcons.of(type?.icon ?: ActivityIcons.DEFAULT),
                null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                type?.name ?: stringResource(R.string.calendar_unknown_activity),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${timeLabel(record.startAt.toLocalDateTime(zone).toLocalTime())} – " +
                    timeLabel(record.endAt.toLocalDateTime(zone).toLocalTime()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(durationText(record.durationMs), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
@ReadOnlyComposable
private fun habitScheduleLabel(habit: Habit): String {
    val locale = currentLocale()
    return when (val schedule = habit.schedule) {
        HabitSchedule.Daily -> stringResource(R.string.calendar_habit_daily)
        is HabitSchedule.Weekdays -> schedule.days
            .sortedBy { it.value }
            .joinToString(" ") { it.getDisplayName(TextStyle.NARROW, locale) }
        is HabitSchedule.WeeklyTimes -> stringResource(R.string.calendar_habit_weekly, schedule.times)
    }
}

@Composable
@ReadOnlyComposable
private fun countdownRelative(countdown: Countdown, today: LocalDate): String = when (countdown.mode) {
    CountdownMode.DOWN -> {
        val days = CountdownMath.daysToTarget(countdown, today)
        when {
            days > 0 -> pluralStringResource(R.plurals.calendar_days_away, days.toInt(), days.toInt())
            days == 0L -> stringResource(R.string.calendar_countdown_today)
            else -> pluralStringResource(R.plurals.calendar_days_ago, (-days).toInt(), (-days).toInt())
        }
    }
    CountdownMode.UP -> {
        val days = CountdownMath.daysSince(countdown, today)
        pluralStringResource(R.plurals.calendar_days_since, days.toInt(), days.toInt())
    }
}
