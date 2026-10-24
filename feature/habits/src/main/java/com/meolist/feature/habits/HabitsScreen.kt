package com.meolist.feature.habits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoPalette
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
) {
    val done: Boolean get() = HabitRules.isDone(habit, value)
}

data class HabitsState(
    val items: List<HabitItem> = emptyList(),
    val archived: List<Habit> = emptyList(),
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
        fun item(habit: Habit): HabitItem {
            val mine = byHabit[habit.id].orEmpty()
            return HabitItem(
                habit = habit,
                value = mine.firstOrNull { it.day == today }?.value ?: 0,
                streak = HabitRules.currentStreak(habit, mine, today, settings.firstDayOfWeek),
            )
        }
        HabitsState(
            items = all.filter { !it.archived }.map(::item),
            archived = all.filter { it.archived },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.habits_title)) },
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
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, stringResource(R.string.habits_new))
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
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(
    item: HabitItem,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = Color(item.habit.color)
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            leadingContent = {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(ActivityIcons.of(item.habit.icon), null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            },
            headlineContent = { Text(item.habit.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.streak > 0) {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            null,
                            tint = color,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            item.streak.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onToggle,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (item.done) color else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            stringResource(if (item.done) R.string.habits_undo_check_in else R.string.habits_check_in),
                            tint = if (item.done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        )
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
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.habits_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.habits_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onAddSuggested(suggestions) }) { Text(stringResource(R.string.habits_add_suggested)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.habits_new)) }
    }
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
                        Icon(ActivityIcons.of(habit.icon), null, tint = Color(habit.color), modifier = Modifier.size(20.dp))
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
