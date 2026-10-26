package com.meolist.feature.habits

import com.meolist.core.ui.components.MeoScaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.meolist.core.ui.accentColor
import com.meolist.core.ui.components.MeoProgressBar
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.readableOn
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.habit.Habit
import com.meolist.domain.habit.HabitCheckIn
import com.meolist.domain.habit.HabitInteractor
import com.meolist.domain.habit.HabitRepository
import com.meolist.domain.habit.HabitRules
import com.meolist.domain.settings.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitDetailState(
    val habit: Habit? = null,
    val checks: List<HabitCheckIn> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val loaded: Boolean = false,
) {
    val todayValue: Int get() = checks.firstOrNull { it.day == today }?.value ?: 0
    val doneToday: Boolean get() = habit != null && HabitRules.isDone(habit, todayValue)
    val streak: Int get() = habit?.let { HabitRules.currentStreak(it, checks, today, firstDayOfWeek) } ?: 0
    val best: Int get() = habit?.let { HabitRules.bestStreak(it, checks, today, firstDayOfWeek) } ?: 0
    val totalDone: Int get() = habit?.let { HabitRules.totalDone(it, checks) } ?: 0

    /** Done and scheduled days of the last 30 days including today. */
    fun last30(): Pair<Int, Int> {
        val h = habit ?: return 0 to 0
        return HabitRules.completion(h, checks, today.minusDays(29), today)
    }
}

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    habits: HabitRepository,
    private val interactor: HabitInteractor,
    prefs: AppPreferences,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val habitId: Long = savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0

    val state: StateFlow<HabitDetailState> = combine(
        habits.observeHabits(),
        habits.observeCheckIns(),
        prefs.settings,
    ) { all, checks, settings ->
        HabitDetailState(
            habit = all.firstOrNull { it.id == habitId },
            checks = checks.filter { it.habitId == habitId },
            today = clock.today(),
            firstDayOfWeek = settings.firstDayOfWeek,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailState())

    fun toggle(day: LocalDate) = appScope.launch { interactor.toggle(habitId, day) }

    fun setValue(day: LocalDate, value: Int) = appScope.launch { interactor.setCheckIn(habitId, day, value) }

    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        val habit = state.value.habit ?: return
        appScope.launch { interactor.setArchived(habit.id, archived) }
        onDone()
    }

    fun delete(onDone: () -> Unit) {
        appScope.launch { interactor.delete(habitId) }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: HabitDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val habit = state.habit
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var month by rememberSaveable { mutableStateOf("") }
    var countedDay by remember { mutableStateOf<LocalDate?>(null) }

    val habitColor = habit?.let { accentColor(it.color) } ?: MaterialTheme.colorScheme.primary

    MeoScaffold(
        title = habit?.name ?: "",
        onBack = onBack,
        actions = {
            if (habit != null) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(com.meolist.core.R.string.core_more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.habits_edit)) },
                            onClick = { menu = false; onEdit(habit.id) },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (habit.archived) R.string.habits_unarchive else R.string.habits_archive,
                                    ),
                                )
                            },
                            onClick = { menu = false; vm.setArchived(!habit.archived, onBack) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.meolist.core.R.string.core_delete)) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (!state.loaded) return@MeoScaffold
        if (habit == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.habits_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@MeoScaffold
        }
        val today = state.today
        val color = habitColor
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = com.meolist.core.theme.Meo.spacing.screen),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                            Icon(ActivityIcons.of(habit.icon), null, tint = color.readableOn(), modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(habit.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                scheduleLabel(habit.schedule),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (habit.isCountable) {
                        val target = habit.dailyTarget
                        val value = state.todayValue
                        Text(
                            stringResource(R.string.habits_progress, value, target, habit.unit).trim(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        MeoProgressBar(
                            progress = (value.toFloat() / target).coerceIn(0f, 1f),
                            color = color,
                            height = 10.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = { vm.setValue(today, state.todayValue - 1) },
                                enabled = state.todayValue > 0,
                            ) { Icon(Icons.Outlined.Remove, stringResource(R.string.habits_decrease)) }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalIconButton(onClick = { vm.setValue(today, state.todayValue + 1) }) {
                                Icon(Icons.Outlined.Add, stringResource(R.string.habits_increase))
                            }
                            Spacer(Modifier.weight(1f))
                            if (state.doneToday) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Check, null, tint = color)
                                    Text(
                                        stringResource(R.string.habits_done),
                                        color = color,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { vm.toggle(today) },
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (state.doneToday) color else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (state.doneToday) color.readableOn() else MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (state.doneToday) R.string.habits_done_today else R.string.habits_check_in_today,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            StatsRow(state)

            Spacer(Modifier.height(12.dp))
            val initialMonth = YearMonth.from(today)
            val displayedMonth = month.takeIf { it.isNotBlank() }?.let { YearMonth.parse(it) } ?: initialMonth
            HabitCalendarCard(
                habit = habit,
                state = state,
                month = displayedMonth,
                onMonth = { month = it.toString() },
                onDayClick = { day ->
                    if (habit.isCountable) countedDay = day else vm.toggle(day)
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.habits_delete_title, habit?.name.orEmpty()),
            text = stringResource(R.string.habits_delete_text),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    countedDay?.let { day ->
        val existing = state.checks.firstOrNull { it.day == day }?.value ?: 0
        CountedDayDialog(
            habit = habit ?: return@let,
            day = day,
            initial = existing,
            onConfirm = { vm.setValue(day, it) },
            onDismiss = { countedDay = null },
        )
    }
}

@Composable
private fun StatsRow(state: HabitDetailState) {
    val (done, scheduled) = state.last30()
    val percent = if (scheduled > 0) done * 100 / scheduled else 0
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 14.dp)) {
            StatCell(stringResource(R.string.habits_streak), state.streak.toString(), Modifier.weight(1f))
            StatCell(stringResource(R.string.habits_best), state.best.toString(), Modifier.weight(1f))
            StatCell(stringResource(R.string.habits_last_30), "$percent%", Modifier.weight(1f))
            StatCell(stringResource(R.string.habits_total), state.totalDone.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HabitCalendarCard(
    habit: Habit,
    state: HabitDetailState,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val color = accentColor(habit.color)
    val locale = currentLocale()
    val byDay = state.checks.associate { it.day to it.value }
    val firstDay = state.firstDayOfWeek
    val currentMonth = YearMonth.from(state.today)
    val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.habits_previous_month))
                }
                Text(
                    java.time.format.DateTimeFormatter.ofPattern(
                        android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM"),
                        locale,
                    ).format(month),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < currentMonth) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.habits_next_month))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val day = DayOfWeek.of((firstDay.value - 1 + i) % 7 + 1)
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            for (week in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val day = gridStart.plusDays((week * 7 + col).toLong())
                        if (day.month != month.month || day.year != month.year) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            DayCell(
                                habit = habit,
                                day = day,
                                value = byDay[day] ?: 0,
                                today = state.today,
                                color = color,
                                onClick = { onDayClick(day) },
                                modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    habit: Habit,
    day: LocalDate,
    value: Int,
    today: LocalDate,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheduled = HabitRules.isScheduled(habit, day)
    val done = HabitRules.isDone(habit, value)
    val partial = value > 0 && !done
    val future = day > today
    val clickable = !future
    val background = when {
        done -> color
        partial -> color.copy(alpha = 0.3f)
        scheduled -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val textColor = when {
        done -> color.readableOn()
        future || !scheduled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(
                if (day == today) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp))
                else Modifier,
            )
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
private fun CountedDayDialog(
    habit: Habit,
    day: LocalDate,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(day) { mutableStateOf(initial) }
    val locale = currentLocale()
    val date = java.time.format.DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMd"),
        locale,
    ).format(day)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(onClick = { value = (value - 1).coerceAtLeast(0) }, enabled = value > 0) {
                    Icon(Icons.Outlined.Remove, stringResource(R.string.habits_decrease))
                }
                Text(
                    stringResource(R.string.habits_progress, value, habit.dailyTarget, habit.unit).trim(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { value = (value + 1).coerceAtMost(habit.dailyTarget) },
                    enabled = value < habit.dailyTarget,
                ) { Icon(Icons.Outlined.Add, stringResource(R.string.habits_increase)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value); onDismiss() }) {
                Text(stringResource(com.meolist.core.R.string.core_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(0); onDismiss() }) {
                Text(stringResource(com.meolist.core.R.string.core_clear))
            }
        },
    )
}
