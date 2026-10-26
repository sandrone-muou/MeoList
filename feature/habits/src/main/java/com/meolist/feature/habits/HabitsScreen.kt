package com.meolist.feature.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.theme.Meo
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoPalette
import com.meolist.core.ui.accentColor
import com.meolist.core.ui.components.EmptyState
import com.meolist.core.ui.components.MeoProgressBar
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.components.ProgressRing
import com.meolist.core.ui.components.rememberConfirmHaptic
import com.meolist.core.ui.currentLocale
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
import java.time.format.TextStyle as JavaTextStyle
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitItem(
    val habit: Habit,
    val value: Int,
    val streak: Int,
    /** Last seven days, oldest first; true when this habit was checked on that day. */
    val recent: List<Boolean> = emptyList(),
) {
    val done: Boolean get() = HabitRules.isDone(habit, value)
}

data class DayCheck(val day: LocalDate, val count: Int)

data class HabitsState(
    val items: List<HabitItem> = emptyList(),
    val archived: List<Habit> = emptyList(),
    val week: List<DayCheck> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val loaded: Boolean = false,
)

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val interactor: HabitInteractor,
    prefs: AppPreferences,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<HabitsState> = combine(
        habits.observeHabits(),
        habits.observeCheckIns(),
        prefs.settings,
    ) { all, checks, settings ->
        val today = clock.today()
        val byHabit = checks.groupBy { it.habitId }
        val active = all.filter { !it.archived }
        fun isDoneOn(habit: Habit, day: LocalDate): Boolean =
            byHabit[habit.id].orEmpty().any { it.day == day && HabitRules.isDone(habit, it.value) }

        fun item(habit: Habit): HabitItem {
            val mine = byHabit[habit.id].orEmpty()
            return HabitItem(
                habit = habit,
                value = mine.firstOrNull { it.day == today }?.value ?: 0,
                streak = HabitRules.currentStreak(habit, mine, today, settings.firstDayOfWeek),
                recent = (6 downTo 0).map { offset -> isDoneOn(habit, today.minusDays(offset.toLong())) },
            )
        }
        HabitsState(
            items = active.map(::item),
            archived = all.filter { it.archived },
            week = (6 downTo 0).map { offset ->
                val day = today.minusDays(offset.toLong())
                DayCheck(day = day, count = active.count { isDoneOn(it, day) })
            },
            today = today,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsState())

    fun toggle(habit: Habit) = appScope.launch {
        interactor.toggle(habit.id, clock.today())
    }

    fun setArchived(habit: Habit, archived: Boolean) = appScope.launch {
        interactor.setArchived(habit.id, archived)
    }

    fun delete(habit: Habit) = appScope.launch { interactor.delete(habit.id) }

    fun addSuggested(items: List<Habit>) = appScope.launch { items.forEach { interactor.save(it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsRoute(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: HabitsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Habit?>(null) }

    MeoScaffold(
        title = stringResource(R.string.habits_title),
        large = true,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(com.meolist.core.R.string.core_more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.habits_archived_count, state.archived.size)) },
                        enabled = state.archived.isNotEmpty(),
                        onClick = { menu = false; showArchived = true },
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.items.isNotEmpty()) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.habits_new))
                }
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Unit
            state.items.isEmpty() -> EmptyHabits(
                onAddSuggested = { vm.addSuggested(it) },
                onAdd = onAdd,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = Meo.spacing.screen,
                    end = Meo.spacing.screen,
                    top = Meo.spacing.xs,
                    bottom = Meo.spacing.bottomContent,
                ),
                verticalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                item(key = "week") { WeekOverview(state) }
                items(state.items, key = { it.habit.id }) { item ->
                    HabitCard(
                        item = item,
                        onOpen = { onOpen(item.habit.id) },
                        onToggle = { vm.toggle(item.habit) },
                        onArchive = { vm.setArchived(item.habit, true) },
                        onDelete = { deleting = item.habit },
                    )
                }
            }
        }
    }

    deleting?.let { habit ->
        ConfirmDialog(
            title = stringResource(R.string.habits_delete_title, habit.name),
            text = stringResource(R.string.habits_delete_text),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { vm.delete(habit) },
            onDismiss = { deleting = null },
        )
    }
    if (showArchived) {
        ArchivedHabitsDialog(
            habits = state.archived,
            onRestore = { vm.setArchived(it, false) },
            onDismiss = { showArchived = false },
        )
    }
}

@Composable
private fun WeekOverview(state: HabitsState) {
    val done = state.items.count { it.done }
    val total = state.items.size
    val percent = if (total == 0) 0 else done * 100 / total
    val bestStreak = state.items.maxOfOrNull { it.streak } ?: 0
    val locale = currentLocale()
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(Meo.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.habits_today_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.habits_week_today, done, total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (bestStreak > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocalFireDepartment,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.habits_longest_streak, bestStreak),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                ProgressRing(
                    progress = if (total == 0) 0f else done.toFloat() / total,
                    size = 64.dp,
                    stroke = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(Meo.spacing.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                state.week.forEach { day ->
                    DayDot(day, today = state.today, locale = locale)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.habits_week_caption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayDot(day: DayCheck, today: LocalDate, locale: java.util.Locale) {
    val isToday = day.day == today
    val completed = day.count > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            day.day.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(Meo.spacing.xs))
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        completed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                    },
                )
                .then(
                    if (isToday) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                Text(
                    day.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            } else {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(
    item: HabitItem,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = accentColor(item.habit.color)
    var menu by remember { mutableStateOf(false) }
    val haptic = rememberConfirmHaptic()
    val buttonColor by animateColorAsState(
        if (item.done) color else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "habitCheckBackground",
    )
    val iconTint by animateColorAsState(
        if (item.done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "habitCheckTint",
    )

    Box {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(Meo.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ActivityIcons.of(item.habit.icon), null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(Meo.spacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.habit.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.streak > 0) {
                            Spacer(Modifier.width(Meo.spacing.xs))
                            Icon(
                                Icons.Outlined.LocalFireDepartment,
                                null,
                                tint = color,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                item.streak.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                            )
                        }
                    }
                    Text(
                        buildString {
                            append(scheduleLabel(item.habit.schedule))
                            if (item.habit.isCountable) {
                                append(" · ")
                                append(
                                    stringResource(
                                        R.string.habits_progress,
                                        item.value,
                                        item.habit.dailyTarget,
                                        item.habit.unit,
                                    ).trim(),
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.habit.isCountable) {
                        Spacer(Modifier.height(6.dp))
                        MeoProgressBar(
                            progress = item.value.toFloat() / item.habit.dailyTarget.coerceAtLeast(1),
                            color = color,
                            height = 4.dp,
                        )
                    }
                    if (item.recent.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.recent.forEach { checked ->
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (checked) {
                                                color
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(Meo.spacing.sm))
                FilledTonalIconButton(
                    onClick = {
                        haptic()
                        onToggle()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = buttonColor,
                        contentColor = iconTint,
                    ),
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        stringResource(if (item.done) R.string.habits_undo_check_in else R.string.habits_check_in),
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.habits_edit)) }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text(stringResource(R.string.habits_archive)) }, onClick = { menu = false; onArchive() })
            DropdownMenuItem(text = { Text(stringResource(com.meolist.core.R.string.core_delete)) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun EmptyHabits(
    onAddSuggested: (List<Habit>) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val suggestions = listOf(
        Habit(
            name = stringResource(R.string.habits_suggest_read),
            icon = "read",
            color = MeoPalette.colors[5],
            startDay = today,
        ),
        Habit(
            name = stringResource(R.string.habits_suggest_water),
            icon = "water",
            color = MeoPalette.colors[3],
            dailyTarget = 8,
            unit = stringResource(R.string.habits_suggest_water_unit),
            startDay = today,
        ),
        Habit(
            name = stringResource(R.string.habits_suggest_meditate),
            icon = "meditate",
            color = MeoPalette.colors[0],
            startDay = today,
        ),
    )
    EmptyState(
        icon = Icons.Outlined.Check,
        title = stringResource(R.string.habits_empty_title),
        body = stringResource(R.string.habits_empty_text),
        modifier = modifier.fillMaxSize(),
        primary = stringResource(R.string.habits_add_suggested) to { onAddSuggested(suggestions) },
        secondary = stringResource(R.string.habits_new) to onAdd,
    )
}

@Composable
private fun ArchivedHabitsDialog(
    habits: List<Habit>,
    onRestore: (Habit) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habits_archived_title)) },
        text = {
            Column {
                habits.forEach { habit ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(ActivityIcons.of(habit.icon), null, tint = accentColor(habit.color), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(habit.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRestore(habit) }) { Text(stringResource(R.string.habits_unarchive)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_ok)) } },
    )
}
