package com.meolist.feature.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meolist.core.theme.Meo
import com.meolist.core.ui.ColorDot
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.components.CalendarNavigator
import com.meolist.core.ui.components.EmptyState
import com.meolist.core.ui.components.MeoListGroup
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.components.MonthDateGrid
import com.meolist.core.ui.components.WeekDateStrip
import com.meolist.core.ui.components.rememberConfirmHaptic
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.longDate
import com.meolist.domain.calendar.CalendarPeriod
import com.meolist.domain.task.RepeatAdvance
import com.meolist.domain.task.TaskFilter
import com.meolist.domain.task.TaskItem
import com.meolist.domain.task.TaskSection
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun TasksRoute(
    onOpenTask: (Long) -> Unit,
    onSearch: () -> Unit,
    onManageLists: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val advance by viewModel.lastAdvance.collectAsStateWithLifecycle()
    TasksScreen(
        state = state,
        advance = advance,
        onUndoRepeat = viewModel::undoRepeat,
        onAdvanceShown = viewModel::consumeAdvance,
        onSelectFilter = viewModel::selectFilter,
        onOpenTask = onOpenTask,
        onSearch = onSearch,
        onManageLists = onManageLists,
        onToggle = { id, done -> viewModel.setDone(id, done) },
        onTrash = viewModel::moveToTrash,
        onRestore = viewModel::restore,
        onDeleteForever = viewModel::deletePermanently,
        onEmptyTrash = viewModel::emptyTrash,
        quickAddDefaults = viewModel::quickAddDefaults,
        onAdd = viewModel::addTask,
        onView = viewModel::selectView,
        onDate = viewModel::selectDate,
        onPeriod = viewModel::selectPeriod,
        onMove = viewModel::moveCalendar,
        onToday = viewModel::goToday,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksScreen(
    state: TasksUiState,
    advance: RepeatAdvance?,
    onUndoRepeat: (RepeatAdvance) -> Unit,
    onAdvanceShown: () -> Unit,
    onSelectFilter: (TaskFilter) -> Unit,
    onOpenTask: (Long) -> Unit,
    onSearch: () -> Unit,
    onManageLists: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onTrash: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
    quickAddDefaults: () -> QuickAddDefaults,
    onAdd: AddTaskCallback,
    onView: (TaskView) -> Unit,
    onDate: (LocalDate) -> Unit,
    onPeriod: (CalendarPeriod) -> Unit,
    onMove: (Long) -> Unit,
    onToday: () -> Unit,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val calendarListState = rememberLazyListState()
    val viewStateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    val movedToTrash = stringResource(R.string.tasks_moved_to_trash)
    val newTaskLabel = stringResource(R.string.tasks_new)
    val undo = stringResource(com.meolist.core.R.string.core_undo)
    RepeatAdvanceEffect(advance, snackbar, state.today, state.zone, onUndo = onUndoRepeat, onConsumed = onAdvanceShown)

    val trashWithUndo: (Long) -> Unit = { id ->
        onTrash(id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(movedToTrash, undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onRestore(id)
        }
    }

    MeoScaffold(
        title = stringResource(R.string.tasks_title),
        large = true,
        actions = {
            if (state.view == TaskView.LIST && state.isTrash && state.groups.isNotEmpty()) {
                IconButton(onClick = { confirmEmptyTrash = true }) {
                    Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.tasks_empty_trash))
                }
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, stringResource(R.string.tasks_search))
            }
        },
        floatingActionButton = {
            if (!state.loading && (state.view == TaskView.CALENDAR ||
                (!state.isTrash && state.filter != TaskFilter.Completed && state.groups.isNotEmpty()))) {
                ExtendedFloatingActionButton(
                    onClick = { showQuickAdd = true },
                    modifier = Modifier.testTag("task-new").semantics { contentDescription = newTaskLabel },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.tasks_new)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        viewStateHolder.SaveableStateProvider(state.view.name) {
            if (state.view == TaskView.LIST) {
                TaskGroupsList(
                    state = state,
                    contentPadding = padding,
                    listState = listState,
                    onView = onView,
                    onShowFilters = { showFilters = true },
                    onOpenTask = onOpenTask,
                    onToggle = onToggle,
                    onSelectFilter = onSelectFilter,
                    onTrash = trashWithUndo,
                    onRestore = onRestore,
                    onDeleteForever = onDeleteForever,
                    onNewTask = { showQuickAdd = true },
                )
            } else {
                TaskCalendar(
                    state, padding, calendarListState, onView, onDate, onPeriod, onMove, onToday,
                    onOpenTask, onToggle, trashWithUndo, onNewTask = { showQuickAdd = true },
                )
            }
        }
    }
    if (showFilters) {
        TaskFilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onSelect = { onSelectFilter(it); showFilters = false },
            onManageLists = { showFilters = false; onManageLists() },
        )
    }

    if (showQuickAdd && !state.loading) {
        QuickAddSheet(
            defaults = remember {
                if (state.view == TaskView.CALENDAR) QuickAddDefaults(state.selectedDate, null, emptySet())
                else quickAddDefaults()
            },
            lists = state.lists.filter { !it.archived },
            tags = state.tags,
            today = state.today,
            onAdd = onAdd,
            onDismiss = { showQuickAdd = false },
        )
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = stringResource(R.string.tasks_empty_trash),
            text = stringResource(R.string.tasks_empty_trash_message),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = onEmptyTrash,
            onDismiss = { confirmEmptyTrash = false },
        )
    }
}

@Composable
private fun TaskGroupsList(
    state: TasksUiState,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onView: (TaskView) -> Unit,
    onShowFilters: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onSelectFilter: (TaskFilter) -> Unit,
    onTrash: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    onNewTask: () -> Unit,
) {
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    val showList = state.filter !is TaskFilter.OfList
    val alwaysExpanded = state.filter == TaskFilter.Completed

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = Meo.spacing.screen,
            end = Meo.spacing.screen,
            top = contentPadding.calculateTopPadding(),
            bottom = Meo.layout.listBottom,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "view") { TaskViewSwitch(state.view, onView) }
        item(key = "filter") {
            Surface(
                onClick = onShowFilters,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(vertical = Meo.spacing.sm).testTag("task-filter"),
            ) {
                Row(
                    Modifier.heightIn(min = Meo.layout.taskFilterRowMinHeight).padding(Meo.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.List, null)
                    Text(filterTitle(state.filter, state), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).padding(horizontal = Meo.spacing.md))
                    state.counts[state.filter.key()]?.let { Text(it.toString(), color = MaterialTheme.colorScheme.primary) }
                    Icon(Icons.Outlined.ExpandMore, stringResource(R.string.tasks_choose_filter))
                }
            }
        }
        if (!state.isTrash) {
            item(key = "overview") { WorkloadCard(state, onSelectFilter) }
        }
        if (state.groups.isEmpty()) {
            item(key = "empty") {
                val completed = state.filter == TaskFilter.Completed
                EmptyState(
                    icon = if (state.isTrash) Icons.Outlined.Delete else Icons.Outlined.CheckCircle,
                    title = stringResource(
                        when {
                            state.isTrash -> R.string.tasks_trash_empty
                            completed -> R.string.tasks_completed_empty
                            else -> R.string.tasks_empty
                        },
                    ),
                    body = stringResource(
                        when {
                            state.isTrash -> R.string.tasks_trash_hint
                            completed -> R.string.tasks_completed_hint
                            else -> R.string.tasks_empty_hint
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = Meo.spacing.xl),
                    primary = if (state.isTrash || completed) {
                        null
                    } else {
                        stringResource(R.string.tasks_new) to onNewTask
                    },
                )
            }
        }
        state.groups.forEach { group ->
            val collapsible = group.section == TaskSection.COMPLETED && !alwaysExpanded
            val expanded = !collapsible || completedExpanded
            item(key = "header-${group.section}") {
                SectionHeader(
                    title = sectionTitle(group.section),
                    count = group.items.size,
                    isOverdue = group.section == TaskSection.OVERDUE,
                    expandable = collapsible,
                    expanded = expanded,
                    onClick = { if (collapsible) completedExpanded = !completedExpanded },
                )
            }
            if (expanded) {
                items(group.items, key = { it.task.id }) { item ->
                    Box(Modifier.animateItem()) {
                        if (state.isTrash) {
                            Column {
                                TaskRow(
                                    item = item,
                                    showList = showList,
                                    today = state.today,
                                    zone = state.zone,
                                    onToggle = {},
                                    onClick = { onOpenTask(item.task.id) },
                                    trailing = {
                                        IconButton(onClick = { onRestore(item.task.id) }) {
                                            Icon(Icons.Outlined.RestoreFromTrash, stringResource(R.string.tasks_restore))
                                        }
                                        IconButton(onClick = { onDeleteForever(item.task.id) }) {
                                            Icon(Icons.Outlined.DeleteForever, stringResource(R.string.tasks_delete_forever))
                                        }
                                    },
                                )
                                TaskRowDivider()
                            }
                        } else {
                            SwipeTask(
                                item = item,
                                onToggle = { done -> onToggle(item.task.id, done) },
                                onTrash = onTrash,
                            ) {
                                Column {
                                    TaskRow(
                                        item = item,
                                        showList = showList,
                                        today = state.today,
                                        zone = state.zone,
                                        onToggle = { onToggle(item.task.id, it) },
                                        onClick = { onOpenTask(item.task.id) },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                                        hasReminder = item.task.id in state.reminderTaskIds,
                                        isTiming = item.task.id in state.timingTaskIds,
                                    )
                                    TaskRowDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskViewSwitch(view: TaskView, onSelect: (TaskView) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = Meo.spacing.sm)) {
        TaskView.entries.forEachIndexed { index, value ->
            SegmentedButton(
                selected = view == value,
                modifier = Modifier.testTag("task-view-${value.name}"),
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, TaskView.entries.size),
                icon = {},
            ) {
                Text(stringResource(if (value == TaskView.LIST) R.string.tasks_view_list else R.string.tasks_view_calendar))
            }
        }
    }
}

@Composable
private fun WorkloadCard(state: TasksUiState, onSelect: (TaskFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Meo.spacing.sm), horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
        listOf(TaskFilter.Today, TaskFilter.Next7Days, TaskFilter.Inbox).forEach { filter ->
            Surface(
                onClick = { onSelect(filter) },
                shape = MaterialTheme.shapes.medium,
                color = if (state.filter == filter) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(Meo.spacing.md), verticalArrangement = Arrangement.spacedBy(Meo.spacing.xs)) {
                    Text((state.counts[filter.key()] ?: 0).toString(), style = MaterialTheme.typography.titleLarge)
                    Text(filterTitle(filter, state), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TaskCalendar(
    state: TasksUiState,
    padding: PaddingValues,
    listState: LazyListState,
    onView: (TaskView) -> Unit,
    onDate: (LocalDate) -> Unit,
    onPeriod: (CalendarPeriod) -> Unit,
    onMove: (Long) -> Unit,
    onToday: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onTrash: (Long) -> Unit,
    onNewTask: () -> Unit,
) {
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    val items = state.calendarTasks[state.selectedDate].orEmpty()
    val (done, open) = items.partition { it.task.isDone }
    val locale = currentLocale()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = Meo.spacing.screen, end = Meo.spacing.screen,
            top = padding.calculateTopPadding(), bottom = Meo.layout.listBottom,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "view") { TaskViewSwitch(state.view, onView) }
        item(key = "calendar") {
            MeoListGroup {
                Column(Modifier.padding(Meo.spacing.sm)) {
                    CalendarNavigator(state.selectedDate, state.calendarPeriod, state.firstDayOfWeek, onPeriod, onMove, onToday)
                    val marker: @Composable (LocalDate) -> Unit = { date ->
                        val count = state.calendarTasks[date]?.size ?: 0
                        Text(
                            if (count > 0) count.toString() else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.calendarPeriod == CalendarPeriod.MONTH) {
                        MonthDateGrid(YearMonth.from(state.selectedDate), state.firstDayOfWeek, state.today, state.selectedDate, onDate, marker)
                    } else {
                        WeekDateStrip(state.selectedDate, state.firstDayOfWeek, state.today, state.selectedDate, onDate, marker = marker)
                    }
                }
            }
        }
        item(key = "day") {
            Row(Modifier.fillMaxWidth().padding(vertical = Meo.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(longDate(state.selectedDate, locale), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(items.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Meo.spacing.sm))
            }
        }
        if (items.isEmpty() && !state.loading) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.Today,
                    title = stringResource(R.string.tasks_calendar_empty),
                    body = stringResource(R.string.tasks_calendar_hint),
                    primary = stringResource(R.string.tasks_new) to onNewTask,
                )
            }
        }
        items(open, key = { it.task.id }) { item ->
            SwipeTask(item, { onToggle(item.task.id, it) }, onTrash) {
                Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                    TaskRow(item, true, state.today, state.zone, { onToggle(item.task.id, it) }, { onOpenTask(item.task.id) },
                        hasReminder = item.task.id in state.reminderTaskIds, isTiming = item.task.id in state.timingTaskIds)
                    TaskRowDivider()
                }
            }
        }
        if (done.isNotEmpty()) {
            item(key = "completed") {
                SectionHeader(stringResource(R.string.tasks_section_completed), done.size, false, true, completedExpanded) {
                    completedExpanded = !completedExpanded
                }
            }
            if (completedExpanded) {
                items(done, key = { it.task.id }) { item ->
                    SwipeTask(item, { onToggle(item.task.id, it) }, onTrash) {
                        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                            TaskRow(item, true, state.today, state.zone, { onToggle(item.task.id, it) }, { onOpenTask(item.task.id) })
                            TaskRowDivider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeTask(
    item: TaskItem,
    onToggle: (Boolean) -> Unit,
    onTrash: (Long) -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = rememberConfirmHaptic()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic()
                    onToggle(!item.task.isDone)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic()
                    onTrash(item.task.id)
                    true
                }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val target = dismissState.targetValue
            val color by animateColorAsState(
                when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> Meo.colors.successContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipeBackground",
            )
            val finish = target == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (finish) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (target != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        if (finish) Icons.Outlined.Check else Icons.Outlined.Delete,
                        null,
                        tint = if (finish) {
                            Meo.colors.onSuccessContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
            }
        },
    ) { content() }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    isOverdue: Boolean,
    expandable: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable, onClick = onClick)
            .padding(start = 4.dp, end = 4.dp, top = Meo.layout.sectionTop, bottom = Meo.layout.sectionBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(start = 6.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        if (expandable) {
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFilterSheet(
    state: TasksUiState,
    onSelect: (TaskFilter) -> Unit,
    onManageLists: () -> Unit,
    onDismiss: () -> Unit,
) {
    var collapsedFolders by rememberSaveable { mutableStateOf(longArrayOf()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Meo.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tasks_choose_filter), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(com.meolist.core.R.string.core_cancel)) }
                }
                DrawerHeader(stringResource(R.string.tasks_smart_filters))
            }
            val smart = listOf(
                Triple(TaskFilter.Inbox, Icons.Outlined.Inbox, R.string.tasks_filter_inbox),
                Triple(TaskFilter.Today, Icons.Outlined.Today, R.string.tasks_filter_today),
                Triple(TaskFilter.Next7Days, Icons.Outlined.DateRange, R.string.tasks_filter_next7),
                Triple(TaskFilter.All, Icons.AutoMirrored.Outlined.ViewList, R.string.tasks_filter_all),
            )
            items(smart, key = { it.first.key() }) { (filter, icon, label) ->
                DrawerItem(stringResource(label), icon, null, state.counts[filter.key()], state.filter == filter) {
                    onSelect(filter)
                }
            }

            item { DrawerHeader(stringResource(R.string.tasks_drawer_lists)) }
            val visibleLists = state.lists.filter { !it.archived }
            val ungrouped = visibleLists.filter { l -> l.folderId == null || state.folders.none { it.id == l.folderId } }
            items(ungrouped, key = { "list-${it.id}" }) { list ->
                val filter = TaskFilter.OfList(list.id)
                DrawerItem(list.name, null, list.color, state.counts[filter.key()], state.filter == filter) {
                    onSelect(filter)
                }
            }
            state.folders.forEach { folder ->
                val inFolder = visibleLists.filter { it.folderId == folder.id }
                item(key = "folder-${folder.id}") {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            collapsedFolders = if (folder.id in collapsedFolders) collapsedFolders.filterNot { it == folder.id }.toLongArray()
                                else collapsedFolders + folder.id
                        }.heightIn(min = Meo.layout.taskFilterRowMinHeight).padding(horizontal = Meo.spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, null)
                        Text(folder.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(horizontal = Meo.spacing.md))
                        Icon(if (folder.id in collapsedFolders) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null)
                    }
                }
                if (folder.id !in collapsedFolders) items(inFolder, key = { "list-${it.id}" }) { list ->
                    val filter = TaskFilter.OfList(list.id)
                    DrawerItem(list.name, null, list.color, state.counts[filter.key()], state.filter == filter, indent = true) {
                        onSelect(filter)
                    }
                }
            }

            if (state.tags.isNotEmpty()) {
                item { DrawerHeader(stringResource(R.string.tasks_drawer_tags)) }
                items(state.tags, key = { "tag-${it.id}" }) { tag ->
                    val filter = TaskFilter.OfTag(tag.id)
                    DrawerItem(tag.name, Icons.Outlined.Tag, null, state.counts[filter.key()], state.filter == filter, tint = Color(tag.color)) {
                        onSelect(filter)
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp)) }
            item {
                DrawerItem(stringResource(R.string.tasks_filter_completed), Icons.Outlined.CheckCircle, null, state.counts[TaskFilter.Completed.key()], state.filter == TaskFilter.Completed) {
                    onSelect(TaskFilter.Completed)
                }
            }
            item {
                DrawerItem(stringResource(R.string.tasks_filter_trash), Icons.Outlined.Delete, null, state.counts[TaskFilter.Trash.key()], state.isTrash) {
                    onSelect(TaskFilter.Trash)
                }
            }
            item {
                DrawerItem(stringResource(R.string.tasks_manage_lists), Icons.Outlined.Settings, null, null, false, onClick = onManageLists)
            }
        }
    }
}

@Composable
private fun DrawerHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector?,
    dotColor: Int?,
    count: Int?,
    selected: Boolean,
    indent: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.padding(horizontal = Meo.spacing.sm)
            .padding(start = if (indent) Meo.spacing.lg else 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = Meo.layout.taskFilterRowMinHeight).padding(horizontal = Meo.spacing.md, vertical = Meo.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                icon != null -> Icon(icon, null, tint = tint ?: LocalContentColor.current)
                dotColor != null -> Box(Modifier.size(Meo.layout.listLeadingIconSize), contentAlignment = Alignment.Center) { ColorDot(dotColor) }
                else -> Icon(Icons.AutoMirrored.Outlined.List, null)
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = Meo.spacing.md))
            if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelLarge)
            if (selected) Icon(Icons.Outlined.Check, null, Modifier.padding(start = Meo.spacing.sm))
        }
    }
}
