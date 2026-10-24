package com.meolist.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.durationText
import com.meolist.core.ui.longDate
import com.meolist.core.ui.timeLabel
import com.meolist.domain.calendar.CalendarDay
import com.meolist.domain.calendar.CalendarRules
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.today
import com.meolist.domain.countdown.CountdownRepository
import com.meolist.domain.habit.HabitInteractor
import com.meolist.domain.habit.HabitRepository
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

enum class CalendarMode { MONTH, WEEK }

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
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val input = MutableStateFlow(
        CalendarInput(CalendarMode.MONTH, clock.today(), clock.today()),
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

    fun setMode(mode: CalendarMode) {
        val current = input.value
        input.value = current.copy(mode = mode, anchor = current.selected)
    }

    fun move(steps: Long) {
        val current = input.value
        val anchor = when (current.mode) {
            CalendarMode.MONTH -> current.anchor.plusMonths(steps)
            CalendarMode.WEEK -> current.anchor.plusWeeks(steps)
        }
        input.value = current.copy(anchor = anchor, selected = anchor)
    }

    fun goToday() {
        val today = clock.today()
        input.value = input.value.copy(anchor = today, selected = today)
    }

    fun select(day: LocalDate) {
        input.value = input.value.copy(selected = day)
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
    vm: CalendarViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locale = currentLocale()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meolist.core.R.string.core_back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::goToday) {
                        Icon(Icons.Outlined.Today, stringResource(R.string.calendar_today))
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
                .padding(horizontal = 12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                CalendarMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { vm.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, CalendarMode.entries.size),
                    ) { Text(stringResource(if (mode == CalendarMode.MONTH) R.string.calendar_month else R.string.calendar_week)) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = { vm.move(-1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_previous))
                }
                Text(
                    title(state, locale),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.move(1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next))
                }
            }

            if (state.mode == CalendarMode.MONTH) {
                MonthGrid(state, onSelect = vm::select)
            } else {
                WeekChart(state, onSelect = vm::select)
            }

            Legend(Modifier.padding(top = 8.dp))

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
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun title(state: CalendarState, locale: java.util.Locale): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM"),
        locale,
    )
    return when (state.mode) {
        CalendarMode.MONTH -> formatter.format(state.anchor)
        CalendarMode.WEEK -> {
            val days = CalendarRules.weekDays(state.anchor, state.firstDayOfWeek)
            val short = java.time.format.DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"),
                locale,
            )
            "${short.format(days.first())} – ${short.format(days.last())}"
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarState, onSelect: (LocalDate) -> Unit) {
    val locale = currentLocale()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val day = DayOfWeek.of((state.firstDayOfWeek.value - 1 + i) % 7 + 1)
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val byDate = state.days.associateBy { it.date }
            val month = state.month
            state.days.map { it.date }.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val inMonth = YearMonth.from(date) == month
                        DayCell(
                            date = date,
                            day = byDate[date],
                            today = state.today,
                            selected = state.selected,
                            inMonth = inMonth,
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: CalendarDay?,
    today: LocalDate,
    selected: LocalDate,
    inMonth: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = date == selected
    val container = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        date == today -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val hasTask = day?.tasks?.isNotEmpty() == true
    val hasHabit = day?.habits?.isNotEmpty() == true
    val hasCountdown = day?.countdowns?.isNotEmpty() == true
    val hasTracked = (day?.trackedMs ?: 0) > 0

    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (inMonth) container else Color.Transparent)
            .then(
                if (date == today || isSelected) {
                    Modifier.border(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(10.dp),
                    )
                } else Modifier,
            )
            .clickable(enabled = inMonth) { onSelect(date) },
    ) {
        Column(
            Modifier.fillMaxSize().padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
            Spacer(Modifier.weight(1f))
            if (inMonth) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (hasTask) Dot(MaterialTheme.colorScheme.primary)
                    if (hasHabit) Dot(MaterialTheme.colorScheme.tertiary)
                    if (hasCountdown) Dot(MaterialTheme.colorScheme.secondary)
                    if (hasTracked) Dot(MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
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
    val locale = currentLocale()
    val axisWidth = 26.dp
    val chartHeight = 230.dp
    val typeColors = state.types.associate { it.id to Color(it.color) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(axisWidth))
                state.days.forEach { day ->
                    val selected = day.date == state.selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onSelect(day.date) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            day.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (day.date == state.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (day.date == state.today) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
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
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.error
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
    val zone = java.time.ZoneId.systemDefault()
    androidx.compose.foundation.Canvas(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .pointerInput(day) { detectTapGestures { onSelect() } },
    ) {
        for (h in listOf(0, 6, 12, 18, 24)) {
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
    onToggleHabit: (com.meolist.domain.habit.Habit) -> Unit,
    onOpenCountdown: (Long) -> Unit,
) {
    val locale = currentLocale()
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    longDate(day.date, locale),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (day.trackedMs > 0) {
                    Text(
                        stringResource(R.string.calendar_tracked, durationText(day.trackedMs)),
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
                SectionTitle(stringResource(R.string.calendar_tasks))
                day.tasks.forEach { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenTask(task.id) }
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(checked = task.isDone, onCheckedChange = { onToggleTask(task) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                                color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            task.dueAt?.takeIf { !task.allDay }?.let { due ->
                                Text(
                                    timeLabel(due.toLocalDateTime(java.time.ZoneId.systemDefault()).toLocalTime()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (day.habits.isNotEmpty()) {
                SectionTitle(stringResource(R.string.calendar_habits))
                day.habits.forEach { habit ->
                    val done = habit.id in day.doneHabitIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenHabit(habit.id) }
                            .padding(vertical = 2.dp),
                    ) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(Color(habit.color)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(ActivityIcons.of(habit.icon), null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(habit.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        FilledTonalIconButton(
                            onClick = { onToggleHabit(habit) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (done) Color(habit.color) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                stringResource(if (done) R.string.calendar_undo_check else R.string.calendar_check),
                                tint = if (done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (day.countdowns.isNotEmpty()) {
                SectionTitle(stringResource(R.string.calendar_countdowns))
                day.countdowns.forEach { countdown ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenCountdown(countdown.id) }
                            .padding(vertical = 6.dp),
                    ) {
                        Dot(Color(countdown.color))
                        Spacer(Modifier.width(8.dp))
                        Text(countdown.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (countdown.yearly) {
                            Text(
                                stringResource(R.string.calendar_yearly),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (day.records.isNotEmpty()) {
                SectionTitle(stringResource(R.string.calendar_time))
                val typeMap = types.associateBy { it.id }
                day.records.forEach { record ->
                    val zone = java.time.ZoneId.systemDefault()
                    val type = typeMap[record.typeId]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Dot(type?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(type?.name ?: stringResource(R.string.calendar_unknown_activity), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${timeLabel(record.startAt.toLocalDateTime(zone).toLocalTime())} – " +
                                    timeLabel(record.endAt.toLocalDateTime(zone).toLocalTime()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(durationText(record.durationMs), style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}
