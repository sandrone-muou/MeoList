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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meolist.core.ui.ColorDot
import com.meolist.core.ui.ConfirmDialog
import com.meolist.domain.task.RepeatAdvance
import com.meolist.domain.task.TaskFilter
import com.meolist.domain.task.TaskItem
import com.meolist.domain.task.TaskSection
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
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    val movedToTrash = stringResource(R.string.tasks_moved_to_trash)
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            TaskDrawer(
                state = state,
                onSelect = {
                    onSelectFilter(it)
                    scope.launch { drawerState.close() }
                },
                onManageLists = {
                    scope.launch { drawerState.close() }
                    onManageLists()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(filterTitle(state.filter, state)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, stringResource(R.string.tasks_open_lists))
                        }
                    },
                    actions = {
                        if (state.isTrash && state.groups.isNotEmpty()) {
                            IconButton(onClick = { confirmEmptyTrash = true }) {
                                Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.tasks_empty_trash))
                            }
                        }
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.tasks_search))
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!state.isTrash && state.filter != TaskFilter.Completed) {
                    FloatingActionButton(onClick = { showQuickAdd = true }) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.tasks_add))
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            if (!state.loading && state.groups.isEmpty()) {
                EmptyState(state.filter, Modifier.padding(padding))
            } else {
                TaskGroupsList(
                    state = state,
                    contentPadding = padding,
                    onOpenTask = onOpenTask,
                    onToggle = onToggle,
                    onTrash = trashWithUndo,
                    onRestore = onRestore,
                    onDeleteForever = onDeleteForever,
                )
            }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            defaults = remember { quickAddDefaults() },
            lists = state.lists.filter { !it.archived },
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
    onOpenTask: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onTrash: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
) {
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    val showList = state.filter !is TaskFilter.OfList
    val alwaysExpanded = state.filter == TaskFilter.Completed

    LazyColumn(
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
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
                    if (state.isTrash) {
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
                    } else {
                        SwipeToTrash(item, onTrash) {
                            TaskRow(
                                item = item,
                                showList = showList,
                                today = state.today,
                                zone = state.zone,
                                onToggle = { onToggle(item.task.id, it) },
                                onClick = { onOpenTask(item.task.id) },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                                hasReminder = item.task.id in state.reminderTaskIds,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToTrash(item: TaskItem, onTrash: (Long) -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onTrash(item.task.id)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    Color.Transparent
                },
                label = "swipeBackground",
            )
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
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
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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

@Composable
private fun EmptyState(filter: TaskFilter, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (filter == TaskFilter.Trash) Icons.Outlined.Delete else Icons.Outlined.CheckCircle,
            null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (filter == TaskFilter.Trash) R.string.tasks_trash_empty else R.string.tasks_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TaskDrawer(
    state: TasksUiState,
    onSelect: (TaskFilter) -> Unit,
    onManageLists: () -> Unit,
) {
    ModalDrawerSheet {
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                Text(
                    stringResource(R.string.tasks_drawer_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
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
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 10.dp, bottom = 4.dp),
                    )
                }
                items(inFolder, key = { "list-${it.id}" }) { list ->
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
                DrawerItem(stringResource(R.string.tasks_filter_completed), Icons.Outlined.CheckCircle, null, null, state.filter == TaskFilter.Completed) {
                    onSelect(TaskFilter.Completed)
                }
            }
            item {
                DrawerItem(stringResource(R.string.tasks_filter_trash), Icons.Outlined.Delete, null, null, state.isTrash) {
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
    NavigationDrawerItem(
        label = { Text(label) },
        icon = {
            when {
                icon != null -> Icon(icon, null, tint = tint ?: androidx.compose.material3.LocalContentColor.current)
                dotColor != null -> Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { ColorDot(dotColor) }
                else -> Icon(Icons.AutoMirrored.Outlined.List, null)
            }
        },
        badge = { if (count != null && count > 0) Badge(containerColor = Color.Transparent) { Text(count.toString()) } },
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .padding(start = if (indent) 12.dp else 0.dp),
    )
}

