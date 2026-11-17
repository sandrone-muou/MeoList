package com.meolist.feature.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meolist.core.theme.Meo
import com.meolist.core.theme.tabular
import com.meolist.core.ui.*
import com.meolist.core.ui.charts.ChartSlice
import com.meolist.core.ui.charts.DonutChart
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.domain.tracker.*
import java.util.Locale

@Composable
fun TimerSessionRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onStopped: (Long?) -> Unit,
    vm: TimerSessionViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by rememberTicker(vm::now)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val permissions = rememberReminderPermissionState()
    var planning by rememberSaveable { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var choosingPosition by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingProject by remember { mutableStateOf<ActivitySubproject?>(null) }
    val timer = state.timer
    val record = state.record
    val completed = record?.stages ?: timer?.stages.orEmpty().filter { it.endAt != null }
    val elapsed = record?.durationMs ?: timer?.let {
        maxOf(now, it.stages.mapNotNull { stage -> stage.endAt }.maxOrNull() ?: it.startedAt) - it.startedAt
    } ?: 0L

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(context.getString(it)); vm.consumeMessage() }
    }

    MeoScaffold(
        title = stringResource(R.string.tracker_session_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (record != null) IconButton(onClick = { onEdit(record.id) }) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.tracker_session_edit))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("session-list"),
            contentPadding = PaddingValues(Meo.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Meo.spacing.md),
        ) {
            if (!state.loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (state.type == null || (vm.finished && record == null)) item {
                Text(stringResource(R.string.tracker_session_missing))
            } else {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(Meo.spacing.lg), verticalArrangement = Arrangement.spacedBy(Meo.spacing.md)) {
                            Text(state.type?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.tracker_session_total), style = MaterialTheme.typography.labelLarge)
                            Text(elapsedText(elapsed), modifier = Modifier.testTag("session-total"), style = MaterialTheme.typography.headlineLarge.tabular())
                            if (!vm.finished) {
                                if (timer == null) {
                                    Button(onClick = {
                                        permissions.requestNotificationsIfNeeded()
                                        vm.start()
                                    }, enabled = !state.busy && state.type?.archived == false, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.tracker_session_start))
                                    }
                                } else {
                                    Button(onClick = vm::mark, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("session-mark")) {
                                        Icon(Icons.Outlined.Flag, null)
                                        Spacer(Modifier.width(Meo.spacing.sm))
                                        Text(stringResource(R.string.tracker_session_mark))
                                    }
                                    OutlinedButton(onClick = { vm.stop(onStopped) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("session-stop")) {
                                        Text(stringResource(R.string.tracker_session_stop))
                                    }
                                }
                                TextButton(onClick = { planning = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.tracker_session_plan))
                                }
                                if (timer == null) Text(stringResource(R.string.tracker_session_plan_hint), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item { Text(stringResource(R.string.tracker_session_completed), style = MaterialTheme.typography.titleMedium) }
                if (completed.isEmpty()) item {
                    Text(stringResource(if (vm.finished) R.string.tracker_session_no_stages else R.string.tracker_session_no_marks), style = MaterialTheme.typography.bodyMedium)
                }
                items(completed, key = { it.position }) { stage ->
                    Card(
                        Modifier.fillMaxWidth().testTag("completed-stage-${stage.position}").clickable(enabled = !state.busy) { choosingPosition = stage.position },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(Meo.spacing.md), verticalArrangement = Arrangement.spacedBy(Meo.spacing.xs)) {
                            Text(stringResource(R.string.tracker_session_stage, stage.position, projectName(stage)), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.tracker_session_stage_time, stage.durationMs / 60_000, stage.durationMs / 1000 % 60), style = MaterialTheme.typography.bodyLarge.tabular())
                        }
                    }
                }
                if (record != null && completed.any { it.durationMs > 0 }) item {
                    SessionChart(completed)
                }
            }
        }
    }

    if (planning) {
        val plan = timer?.let { running -> running.stages.ifEmpty { listOf(TimerStages.current(running)) } }
            ?: state.draft.mapIndexed { index, projectId ->
                TimerStage(index + 1).assignedTo(state.projects.find { it.id == projectId })
            }
        AlertDialog(
            onDismissRequest = { planning = false },
            title = { Text(stringResource(R.string.tracker_session_plan)) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.tracker_session_plan_hint), style = MaterialTheme.typography.bodySmall)
                    plan.forEach { stage ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { choosingPosition = stage.position }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.tracker_session_stage, stage.position, projectName(stage)))
                            }
                            if (stage.startAt == null) IconButton(onClick = { vm.removeStage(stage.position) }, enabled = !state.busy) {
                                Icon(Icons.Outlined.DeleteOutline, stringResource(com.meolist.core.R.string.core_delete))
                            }
                        }
                    }
                    TextButton(onClick = vm::addStage, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tracker_session_add_stage))
                    }
                    TextButton(onClick = { planning = false; managing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tracker_session_projects))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { planning = false }) { Text(stringResource(com.meolist.core.R.string.core_ok)) } },
        )
    }

    choosingPosition?.let { position ->
        AlertDialog(
            onDismissRequest = { choosingPosition = null },
            title = { Text(stringResource(R.string.tracker_session_assignment, position)) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { vm.assign(position, null); choosingPosition = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.tracker_session_unassigned))
                    }
                    state.projects.filter { !it.archived }.forEach { project ->
                        TextButton(onClick = { vm.assign(position, project.id); choosingPosition = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(project.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingPosition = null; planning = false; managing = true }) { Text(stringResource(R.string.tracker_session_projects)) } },
        )
    }

    if (managing) AlertDialog(
        onDismissRequest = { managing = false },
        title = { Text(stringResource(R.string.tracker_session_projects)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                state.projects.forEachIndexed { index, project ->
                    Column(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { editingProject = project }, enabled = !state.busy) {
                            Text(project.name + if (project.archived) stringResource(R.string.tracker_session_archived_suffix) else "")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { vm.moveProject(project, -1) }, enabled = !state.busy && index > 0) {
                                Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.tracker_session_move_up))
                            }
                            IconButton(onClick = { vm.moveProject(project, 1) }, enabled = !state.busy && index < state.projects.lastIndex) {
                                Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.tracker_session_move_down))
                            }
                            TextButton(onClick = { vm.saveProject(project.copy(archived = !project.archived)) }, enabled = !state.busy) {
                                Text(stringResource(if (project.archived) R.string.tracker_unarchive else R.string.tracker_archive))
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    state.type?.let { editingProject = ActivitySubproject(typeId = it.id, name = "", color = ActivityPalette.colors[state.projects.size % ActivityPalette.colors.size]) }
                }, enabled = !state.busy) { Text(stringResource(R.string.tracker_session_add_project)) }
            }
        },
        confirmButton = { TextButton(onClick = { managing = false; planning = !vm.finished }) { Text(stringResource(com.meolist.core.R.string.core_ok)) } },
    )

    editingProject?.let { project ->
        key(project.id) {
            var name by rememberSaveable { mutableStateOf(project.name) }
            var color by rememberSaveable { mutableIntStateOf(project.color) }
            AlertDialog(
                onDismissRequest = { editingProject = null },
                title = { Text(stringResource(R.string.tracker_session_project)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Meo.spacing.md)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.tracker_session_project_name)) }, modifier = Modifier.fillMaxWidth())
                        ColorPicker(selected = color, onSelect = { color = it }, colors = ActivityPalette.colors + project.color)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.saveProject(project.copy(name = name, color = color)); editingProject = null }, enabled = name.isNotBlank() && !state.busy) { Text(stringResource(com.meolist.core.R.string.core_save)) } },
                dismissButton = { TextButton(onClick = { editingProject = null }) { Text(stringResource(com.meolist.core.R.string.core_cancel)) } },
            )
        }
    }
}

@Composable
private fun projectName(stage: TimerStage): String = stage.name.ifBlank { stringResource(R.string.tracker_session_unassigned) }

@Composable
private fun SessionChart(stages: List<TimerStage>) {
    val groups = stages.filter { it.durationMs > 0 }.groupBy { it.subprojectId ?: 0L }
    val total = stages.sumOf { it.durationMs }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    Card(Modifier.fillMaxWidth().testTag("session-chart"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(Meo.spacing.md), verticalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
            Text(stringResource(R.string.tracker_session_chart), style = MaterialTheme.typography.titleMedium)
            DonutChart(
                slices = groups.map { (id, list) -> ChartSlice(id, list.sumOf { it.durationMs }.toFloat(), accentColor(list.first().color)) },
                modifier = Modifier.fillMaxWidth(),
                selectedKey = selected, onSelect = { selected = it },
                description = stringResource(R.string.tracker_session_chart),
            )
            groups.forEach { (id, list) ->
                val time = list.sumOf { it.durationMs }
                Column(Modifier.fillMaxWidth().clickable { selected = id.takeIf { selected != id } }.padding(vertical = Meo.spacing.xs)) {
                    Text(projectName(list.first()), color = accentColor(list.first().color), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.tracker_session_share, elapsedText(time), String.format(Locale.getDefault(), "%.1f", time * 100.0 / total)), style = MaterialTheme.typography.bodyMedium.tabular())
                }
            }
        }
    }
}
