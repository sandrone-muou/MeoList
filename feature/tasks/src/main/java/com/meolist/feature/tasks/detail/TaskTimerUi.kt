package com.meolist.feature.tasks.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.durationText
import com.meolist.core.ui.elapsedText
import com.meolist.core.ui.rememberReminderPermissionState
import com.meolist.core.ui.rememberTicker
import com.meolist.domain.tracker.ActivityType
import com.meolist.feature.tasks.R

/**
 * Starts and stops activity timers linked to the task. Tapping the row starts the default activity
 * (the list's, or the one last used for the task); the arrow picks another one.
 */
@Composable
internal fun TimerRow(
    timer: TaskTimerState,
    canSetListDefault: Boolean,
    editable: Boolean,
    vm: TaskDetailViewModel,
    onAddActivityType: () -> Unit,
) {
    val now by rememberTicker(vm::now)
    val permissions = rememberReminderPermissionState()
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val askPermissionOnce = {
        if (!askedNotifications) {
            askedNotifications = true
            permissions.requestNotificationsIfNeeded()
        }
    }

    val runningMs = timer.running.sumOf { (now - it.timer.startedAt).coerceAtLeast(0) }
    val total = timer.recordedMs + runningMs
    DetailRow(
        icon = Icons.Outlined.Timer,
        label = stringResource(R.string.tasks_timer_start),
        value = if (total >= 60_000) stringResource(R.string.tasks_timer_total, durationText(total)) else "",
        enabled = editable,
        onClick = {
            askPermissionOnce()
            vm.startTimer(onPickType = { picking = true })
        },
        trailing = {
            IconButton(onClick = { picking = true }, enabled = editable) {
                Icon(Icons.Outlined.ArrowDropDown, stringResource(R.string.tasks_timer_pick))
            }
        },
    )
    timer.running.forEach { (running, type) ->
        ListItem(
            headlineContent = { Text(type.name) },
            supportingContent = {
                Text(
                    elapsedText((now - running.startedAt).coerceAtLeast(0)),
                    color = Color(type.color),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            leadingContent = { Icon(ActivityIcons.of(type.icon), null, tint = Color(type.color)) },
            trailingContent = {
                FilledTonalIconButton(onClick = { vm.stopTimer(running.id) }) {
                    Icon(Icons.Outlined.Stop, stringResource(R.string.tasks_timer_stop))
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(start = 16.dp),
        )
    }

    if (picking) {
        ActivityPickerDialog(
            types = timer.types,
            canSetListDefault = canSetListDefault,
            onPick = { typeId, keepForList ->
                picking = false
                askPermissionOnce()
                vm.startTimer(typeId, keepForList)
            },
            onAddActivityType = {
                picking = false
                onAddActivityType()
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ActivityPickerDialog(
    types: List<ActivityType>,
    canSetListDefault: Boolean,
    onPick: (typeId: Long, rememberForList: Boolean) -> Unit,
    onAddActivityType: () -> Unit,
    onDismiss: () -> Unit,
) {
    var rememberForList by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_timer_pick)) },
        text = {
            Column {
                if (types.isEmpty()) {
                    Text(stringResource(R.string.tasks_timer_no_types))
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(types, key = { it.id }) { type ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(type.id, rememberForList) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(ActivityIcons.of(type.icon), null, tint = Color(type.color))
                                Spacer(Modifier.width(16.dp))
                                Text(type.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (canSetListDefault) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { rememberForList = !rememberForList },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = rememberForList, onCheckedChange = { rememberForList = it })
                            Text(stringResource(R.string.tasks_timer_list_default), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddActivityType) { Text(stringResource(R.string.tasks_timer_new_type)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
        },
    )
}
