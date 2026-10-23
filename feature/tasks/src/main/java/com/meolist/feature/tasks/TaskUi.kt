package com.meolist.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meolist.core.ui.ColorDot
import com.meolist.core.ui.relativeDateLabel
import com.meolist.core.ui.timeLabel
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.toMillis
import com.meolist.domain.task.Priority
import com.meolist.domain.task.TaskFilter
import com.meolist.domain.task.TaskItem
import com.meolist.domain.task.TaskSection
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal fun dueMillis(date: LocalDate, time: LocalTime?, zone: ZoneId): Long =
    date.atTime(time ?: LocalTime.MIDNIGHT).toMillis(zone)

@Composable
@ReadOnlyComposable
internal fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> Color(0xFFE53935)
    Priority.MEDIUM -> Color(0xFFFB8C00)
    Priority.LOW -> Color(0xFF1E88A8)
    Priority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
@ReadOnlyComposable
internal fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.HIGH -> R.string.tasks_priority_high
        Priority.MEDIUM -> R.string.tasks_priority_medium
        Priority.LOW -> R.string.tasks_priority_low
        Priority.NONE -> R.string.tasks_priority_none
    },
)

@Composable
@ReadOnlyComposable
internal fun sectionTitle(section: TaskSection): String = stringResource(
    when (section) {
        TaskSection.OVERDUE -> R.string.tasks_section_overdue
        TaskSection.TODAY -> R.string.tasks_section_today
        TaskSection.TOMORROW -> R.string.tasks_section_tomorrow
        TaskSection.NEXT_7_DAYS -> R.string.tasks_section_next7
        TaskSection.LATER -> R.string.tasks_section_later
        TaskSection.NO_DATE -> R.string.tasks_section_no_date
        TaskSection.COMPLETED -> R.string.tasks_section_completed
        TaskSection.TRASH -> R.string.tasks_filter_trash
    },
)

@Composable
@ReadOnlyComposable
internal fun filterTitle(filter: TaskFilter, state: TasksUiState): String = when (filter) {
    TaskFilter.Inbox -> stringResource(R.string.tasks_filter_inbox)
    TaskFilter.Today -> stringResource(R.string.tasks_filter_today)
    TaskFilter.Next7Days -> stringResource(R.string.tasks_filter_next7)
    TaskFilter.All -> stringResource(R.string.tasks_filter_all)
    TaskFilter.Completed -> stringResource(R.string.tasks_filter_completed)
    TaskFilter.Trash -> stringResource(R.string.tasks_filter_trash)
    is TaskFilter.OfList -> state.lists.firstOrNull { it.id == filter.listId }?.name.orEmpty()
    is TaskFilter.OfTag -> "#" + state.tags.firstOrNull { it.id == filter.tagId }?.name.orEmpty()
}

/** Due date (and time) label for a task, e.g. "Tomorrow 09:30". */
@Composable
@ReadOnlyComposable
internal fun dueLabel(dueAt: Long, allDay: Boolean, today: LocalDate, zone: ZoneId): String {
    val dateTime = dueAt.toLocalDateTime(zone)
    val date = relativeDateLabel(dateTime.toLocalDate(), today)
    return if (allDay) date else "$date ${timeLabel(dateTime.toLocalTime())}"
}

@Composable
internal fun TaskCheckbox(done: Boolean, priority: Priority, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val color = priorityColor(priority)
    Checkbox(
        checked = done,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.outline,
            uncheckedColor = color,
        ),
    )
}

@Composable
internal fun TaskRow(
    item: TaskItem,
    showList: Boolean,
    today: LocalDate,
    zone: ZoneId,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val task = item.task
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckbox(task.isDone, task.priority, onToggle, enabled = !task.isDeleted)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isDone) muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val hasMeta = task.dueAt != null || (showList && item.list != null) || item.subtaskTotal > 0 ||
                item.tags.isNotEmpty() || task.note.isNotBlank()
            if (hasMeta) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    task.dueAt?.let { due ->
                        val overdue = !task.isDone && due.toLocalDateTime(zone).toLocalDate() < today
                        MetaText(
                            dueLabel(due, task.allDay, today, zone),
                            if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (item.subtaskTotal > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Checklist, null, Modifier.size(14.dp), tint = muted)
                            Spacer(Modifier.width(2.dp))
                            MetaText("${item.subtaskDone}/${item.subtaskTotal}", muted)
                        }
                    }
                    if (task.note.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(14.dp), tint = muted)
                    }
                    item.tags.take(3).forEach { tag -> MetaText("#${tag.name}", Color(tag.color)) }
                    if (showList && item.list != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(item.list!!.color, size = 8.dp)
                            Spacer(Modifier.width(4.dp))
                            MetaText(item.list!!.name, muted)
                        }
                    }
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun MetaText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
