package com.meolist.feature.tracker

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoPalette
import com.meolist.core.ui.MeoTimePickerDialog
import com.meolist.core.ui.durationText
import com.meolist.core.ui.elapsedText
import com.meolist.core.ui.rememberReminderPermissionState
import com.meolist.core.ui.rememberTicker
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.TrackerTime
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerRoute(
    onEditType: (Long) -> Unit,
    onRecords: () -> Unit,
    vm: TrackerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by rememberTicker(vm::now)
    val permissions = rememberReminderPermissionState()
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var editingRunning by remember { mutableStateOf<TypeCard?>(null) }
    var discarding by remember { mutableStateOf<RunningTimer?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tracker_title)) },
                actions = {
                    IconButton(onClick = onRecords) {
                        Icon(Icons.Outlined.History, stringResource(R.string.tracker_records_title))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(com.meolist.core.R.string.core_more_options))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (state.runningCount > 0) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tracker_stop_all)) },
                                    onClick = { menu = false; vm.stopAll() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tracker_archived_types, state.archived.size)) },
                                enabled = state.archived.isNotEmpty(),
                                onClick = { menu = false; showArchived = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.cards.isNotEmpty()) {
                FloatingActionButton(onClick = { onEditType(0) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.tracker_new_type))
                }
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Unit
            state.cards.isEmpty() -> EmptyTracker(
                onAddSuggested = { suggestions -> vm.addSuggested(suggestions) },
                onAddType = { onEditType(0) },
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                if (state.runningCount > 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.tracker_running_count, state.runningCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                items(state.cards, key = { it.type.id }) { card ->
                    TypeCardView(
                        card = card,
                        windows = state.windows,
                        now = now,
                        onToggle = {
                            if (card.running == null && !askedNotifications) {
                                askedNotifications = true
                                permissions.requestNotificationsIfNeeded()
                            }
                            vm.toggle(card)
                        },
                        onEdit = { onEditType(card.type.id) },
                        onEditRunning = { editingRunning = card },
                        onDiscard = { card.running?.let { discarding = it } },
                        onArchive = { vm.setArchived(card.type.id, true) },
                    )
                }
            }
        }
    }

    editingRunning?.let { card ->
        val timer = card.running
        if (timer == null) {
            editingRunning = null
        } else {
            RunningEditDialog(
                card = card,
                timer = timer,
                now = now,
                onSave = { start, comment -> vm.updateRunning(timer, start, comment) },
                onDismiss = { editingRunning = null },
            )
        }
    }
    discarding?.let { timer ->
        ConfirmDialog(
            title = stringResource(R.string.tracker_discard_title),
            text = stringResource(R.string.tracker_discard_text),
            confirmLabel = stringResource(R.string.tracker_discard),
            onConfirm = { vm.discard(timer.id) },
            onDismiss = { discarding = null },
        )
    }
    if (showArchived) {
        ArchivedTypesDialog(
            types = state.archived,
            onRestore = { vm.setArchived(it.id, false) },
            onEdit = { showArchived = false; onEditType(it.id) },
            onDismiss = { showArchived = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypeCardView(
    card: TypeCard,
    windows: Map<GoalPeriod, PeriodWindow>,
    now: Long,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onEditRunning: () -> Unit,
    onDiscard: () -> Unit,
    onArchive: () -> Unit,
) {
    val color = Color(card.type.color)
    val running = card.running
    var menu by remember { mutableStateOf(false) }

    fun periodMs(period: GoalPeriod): Long {
        val window = windows[period] ?: return 0
        val live = running?.let { TrackerTime.overlapMs(it.startedAt, now, window.from, window.to) } ?: 0
        return (card.recordedMs[period] ?: 0) + live
    }

    Box {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (running != null) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .combinedClickable(onClick = onToggle, onLongClick = { menu = true }),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(ActivityIcons.of(card.type.icon), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        card.type.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (running != null) {
                        Icon(
                            Icons.Outlined.StopCircle,
                            stringResource(R.string.tracker_stop),
                            tint = color,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (running != null) {
                    Text(
                        elapsedText(now - running.startedAt),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    )
                    val subtitle = card.runningTaskTitle ?: running.comment.takeIf { it.isNotBlank() }
                    Text(
                        subtitle ?: stringResource(R.string.tracker_today_total, durationText(periodMs(GoalPeriod.DAY))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        stringResource(R.string.tracker_today_total, durationText(periodMs(GoalPeriod.DAY))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                card.goal?.let { goal ->
                    val done = periodMs(goal.period)
                    val target = goal.targetSec * 1000
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (done.toFloat() / target).coerceIn(0f, 1f) },
                        color = color,
                        trackColor = color.copy(alpha = 0.2f),
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(goalLabel(goal.period), durationText(done), durationText(target)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (done >= target) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (running != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tracker_adjust_running)) },
                    onClick = { menu = false; onEditRunning() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tracker_discard)) },
                    onClick = { menu = false; onDiscard() },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.tracker_edit_type)) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.tracker_archive)) }, onClick = { menu = false; onArchive() })
        }
    }
}

internal fun goalLabel(period: GoalPeriod): Int = when (period) {
    GoalPeriod.DAY -> R.string.tracker_goal_progress_day
    GoalPeriod.WEEK -> R.string.tracker_goal_progress_week
    GoalPeriod.MONTH -> R.string.tracker_goal_progress_month
}

@Composable
private fun EmptyTracker(
    onAddSuggested: (List<ActivityType>) -> Unit,
    onAddType: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = listOf(
        Triple(R.string.tracker_suggest_work, "work", 4),
        Triple(R.string.tracker_suggest_study, "study", 3),
        Triple(R.string.tracker_suggest_read, "read", 5),
        Triple(R.string.tracker_suggest_sport, "sport", 8),
    ).mapIndexed { index, (name, icon, colorIndex) ->
        ActivityType(name = stringResource(name), icon = icon, color = MeoPalette.colors[colorIndex], sortOrder = index.toLong())
    }
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.tracker_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tracker_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onAddSuggested(suggestions) }) { Text(stringResource(R.string.tracker_add_suggested)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddType) { Text(stringResource(R.string.tracker_new_type)) }
    }
}

@Composable
private fun RunningEditDialog(
    card: TypeCard,
    timer: RunningTimer,
    now: Long,
    onSave: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var start by remember { mutableStateOf(timer.startedAt) }
    var comment by remember { mutableStateOf(timer.comment) }
    var pickTime by remember { mutableStateOf(false) }
    val startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), zone)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.type.name) },
        text = {
            Column {
                Text(stringResource(R.string.tracker_started_at), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { pickTime = true }) {
                    Text(
                        "%02d:%02d".format(startTime.hour, startTime.minute) + "  ·  " + elapsedText(now - start),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.tracker_comment)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(start, comment); onDismiss() }) {
                Text(stringResource(com.meolist.core.R.string.core_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
        },
    )
    if (pickTime) {
        MeoTimePickerDialog(
            initial = startTime.toLocalTime(),
            onConfirm = { time ->
                // Same day as the current start; a time still ahead of now means the day before.
                var picked = startTime.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (picked > now) picked -= 24 * 3_600_000L
                start = picked
            },
            onDismiss = { pickTime = false },
        )
    }
}

@Composable
private fun ArchivedTypesDialog(
    types: List<ActivityType>,
    onRestore: (ActivityType) -> Unit,
    onEdit: (ActivityType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracker_archived_title)) },
        text = {
            Column {
                types.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(ActivityIcons.of(type.icon), null, tint = Color(type.color), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { onEdit(type) }, modifier = Modifier.weight(1f)) {
                            Text(type.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = { onRestore(type) }) { Text(stringResource(R.string.tracker_unarchive)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_ok)) } },
    )
}
