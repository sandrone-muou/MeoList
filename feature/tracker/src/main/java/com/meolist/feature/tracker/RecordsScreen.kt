package com.meolist.feature.tracker

import com.meolist.core.ui.components.EmptyState
import com.meolist.core.ui.components.MeoScaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StackedBarChart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.accentColor
import com.meolist.core.ui.MeoDatePickerDialog
import com.meolist.core.ui.charts.DayTimeline
import com.meolist.core.ui.charts.TimelineSpan
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.durationText
import com.meolist.core.ui.elapsedText
import com.meolist.core.ui.longDate
import com.meolist.core.ui.relativeDateLabel
import com.meolist.core.ui.rememberTicker
import com.meolist.core.ui.timeLabel
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.common.toLocalDate
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.today
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.RunningTimer
import com.meolist.domain.tracker.TimeRecord
import com.meolist.domain.tracker.TimeRecordRepository
import com.meolist.domain.tracker.TimerInteractor
import com.meolist.domain.tracker.TrackerTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordRow(val record: TimeRecord, val type: ActivityType?, val taskTitle: String?)

data class RunningRow(val timer: RunningTimer, val type: ActivityType?, val taskTitle: String?)

data class RecordsState(
    val day: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val rows: List<RecordRow> = emptyList(),
    val running: List<RunningRow> = emptyList(),
    /** Time of the day covered by finished records (parts outside the day excluded). */
    val recordedMs: Long = 0,
    val dayStart: Long = 0,
    val dayEnd: Long = 0,
) {
    /** Recorded time plus running timers measured up to [now]. */
    fun totalAt(now: Long): Long = recordedMs + running.sumOf {
        TrackerTime.overlapMs(it.timer.startedAt, maxOf(now, it.timer.startedAt), dayStart, dayEnd)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val records: TimeRecordRepository,
    types: ActivityTypeRepository,
    private val tasks: TaskRepository,
    private val timers: TimerInteractor,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val day = MutableStateFlow(clock.today())

    private val dayData = day.flatMapLatest { d ->
        val zone = clock.zone()
        combine(
            records.observeRecords(d.startOfDayMillis(zone), d.plusDays(1).startOfDayMillis(zone)),
            records.observeRunning(),
        ) { list, running ->
            val relevant = running.filter { it.startedAt < d.plusDays(1).startOfDayMillis(zone) }
            val ids = (list.mapNotNull { it.taskId } + relevant.mapNotNull { it.taskId }).distinct()
            val titles = ids.mapNotNull { id -> tasks.get(id)?.let { id to it.title } }.toMap()
            Triple(list, relevant, titles)
        }
    }

    val state: StateFlow<RecordsState> = combine(day, dayData, types.observeTypes()) { d, (list, running, titles), allTypes ->
        val zone = clock.zone()
        val typeMap = allTypes.associateBy { it.id }
        val from = d.startOfDayMillis(zone)
        val to = d.plusDays(1).startOfDayMillis(zone)
        val now = clock.now()
        RecordsState(
            day = d,
            today = clock.today(),
            zone = zone,
            rows = list.map { RecordRow(it, typeMap[it.typeId], it.taskId?.let(titles::get)) },
            running = running
                .filter { TrackerTime.overlapMs(it.startedAt, maxOf(now, it.startedAt), from, to) > 0 }
                .map { RunningRow(it, typeMap[it.typeId], it.taskId?.let(titles::get)) },
            recordedMs = TrackerTime.totalMs(list, emptyList(), from, to, now),
            dayStart = from,
            dayEnd = to,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsState())

    fun now(): Long = clock.now()

    fun setDay(value: LocalDate) {
        day.value = minOf(value, clock.today())
    }

    fun delete(id: Long, onDeleted: (TimeRecord) -> Unit) = viewModelScope.launch {
        timers.deleteRecord(id)?.let(onDeleted)
    }

    fun restore(record: TimeRecord) = appScope.launch { timers.restoreRecord(record) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsRoute(
    onBack: () -> Unit,
    onEditRecord: (Long) -> Unit,
    onAddRecord: (LocalDate) -> Unit,
    onStats: () -> Unit,
    vm: RecordsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by rememberTicker(vm::now)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pickDate by remember { mutableStateOf(false) }
    val deletedMessage = stringResource(R.string.tracker_record_deleted)
    val undoLabel = stringResource(com.meolist.core.R.string.core_undo)

    MeoScaffold(
        title = stringResource(R.string.tracker_records_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onStats) {
                Icon(Icons.Outlined.StackedBarChart, stringResource(R.string.tracker_records_stats))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.rows.isNotEmpty() || state.running.isNotEmpty()) {
                FloatingActionButton(onClick = { onAddRecord(state.day) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.tracker_record_add))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { vm.setDay(state.day.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.tracker_previous_day))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { pickDate = true }.padding(4.dp),
                ) {
                    Text(
                        relativeDateLabel(state.day, state.today).let { label ->
                            if (state.day == state.today || state.day == state.today.minusDays(1)) {
                                "$label · ${longDate(state.day, currentLocale())}"
                            } else {
                                longDate(state.day, currentLocale())
                            }
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.tracker_day_total, durationText(state.totalAt(now))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.setDay(state.day.plusDays(1)) }, enabled = state.day < state.today) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.tracker_next_day))
                }
            }

            val spans = buildList {
                state.rows.forEach { row ->
                    val color = row.type?.let { accentColor(it.color) } ?: Color.Gray
                    add(
                        TimelineSpan(
                            startMinute = minuteOfDay(row.record.startAt, state.day, state.zone, start = true),
                            endMinute = minuteOfDay(row.record.endAt, state.day, state.zone, start = false),
                            color = color,
                        ),
                    )
                }
                state.running.forEach { row ->
                    add(
                        TimelineSpan(
                            startMinute = minuteOfDay(row.timer.startedAt, state.day, state.zone, start = true),
                            endMinute = minuteOfDay(now, state.day, state.zone, start = false),
                            color = (row.type?.let { accentColor(it.color) } ?: Color.Gray).copy(alpha = 0.55f),
                        ),
                    )
                }
            }.filter { it.endMinute > it.startMinute }
            DayTimeline(
                spans = spans,
                nowMinute = if (state.day == state.today) now.toLocalDateTime(state.zone).toLocalTime().let {
                    it.hour * 60f + it.minute + it.second / 60f
                } else null,
                description = stringResource(R.string.tracker_timeline_desc),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (state.rows.isEmpty() && state.running.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.Timer,
                        title = stringResource(R.string.tracker_no_records),
                        body = stringResource(R.string.tracker_no_records_body),
                        primary = stringResource(R.string.tracker_record_add) to { onAddRecord(state.day) },
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                    if (state.running.isNotEmpty()) {
                        item(key = "running") {
                            Text(
                                stringResource(R.string.tracker_running_count, state.running.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(state.running, key = { "r${it.timer.id}" }) { row ->
                            RunningItem(row, now)
                        }
                    }
                    items(state.rows, key = { it.record.id }) { row ->
                        RecordItem(
                            row = row,
                            day = state.day,
                            zone = state.zone,
                            onClick = { onEditRecord(row.record.id) },
                            onDelete = {
                                vm.delete(row.record.id) { deleted ->
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val result = snackbar.showSnackbar(deletedMessage, undoLabel, withDismissAction = true)
                                        if (result == SnackbarResult.ActionPerformed) vm.restore(deleted)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (pickDate) {
        MeoDatePickerDialog(initial = state.day, onConfirm = vm::setDay, onDismiss = { pickDate = false })
    }
}

/** Minute of [day] (0..1440) of [ms], clamped for records that start before or end after the day. */
private fun minuteOfDay(ms: Long, day: LocalDate, zone: ZoneId, start: Boolean): Float {
    val date = ms.toLocalDate(zone)
    if (date < day) return 0f
    if (date > day) return 1440f
    val time = ms.toLocalDateTime(zone).toLocalTime()
    return time.hour * 60f + time.minute + if (!start && time.second >= 30) 1f else 0f
}

@Composable
private fun RunningItem(row: RunningRow, now: Long) {
    val color = row.type?.let { accentColor(it.color) } ?: MaterialTheme.colorScheme.outline
    val detail = listOfNotNull(row.taskTitle, row.timer.comment.takeIf { it.isNotBlank() }).joinToString(" · ")
    ListItem(
        leadingContent = {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(ActivityIcons.of(row.type?.icon ?: ""), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        },
        headlineContent = { Text(row.type?.name ?: "?") },
        supportingContent = {
            Column {
                Text(
                    stringResource(R.string.tracker_record_running_since, timeLabel(row.timer.startedAt.toLocalDateTime(ZoneId.systemDefault()).toLocalTime())),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (detail.isNotEmpty()) Text(detail, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            Text(elapsedText(now - row.timer.startedAt), style = MaterialTheme.typography.labelLarge, color = color)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecordItem(row: RecordRow, day: LocalDate, zone: ZoneId, onClick: () -> Unit, onDelete: () -> Unit) {
    val record = row.record
    val color = row.type?.let { accentColor(it.color) } ?: MaterialTheme.colorScheme.outline
    val start = record.startAt.toLocalDateTime(zone)
    val end = record.endAt.toLocalDateTime(zone)
    // Mark ends that fall on another day, e.g. a record running past midnight.
    val startText = timeLabel(start.toLocalTime()) + if (start.toLocalDate() != day) " (${start.toLocalDate().dayOfMonth})" else ""
    val endText = timeLabel(end.toLocalTime()) + if (end.toLocalDate() != day) " (${end.toLocalDate().dayOfMonth})" else ""
    val detail = listOfNotNull(
        row.taskTitle,
        record.comment.takeIf { it.isNotBlank() },
        stringResource(R.string.tracker_record_manual).takeIf { record.isManual },
    ).joinToString(" · ")
    ListItem(
        leadingContent = {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(ActivityIcons.of(row.type?.icon ?: ""), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        },
        headlineContent = { Text(row.type?.name ?: "?") },
        supportingContent = {
            Column {
                Text("$startText – $endText")
                if (detail.isNotEmpty()) Text(detail, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(durationText(record.durationMs), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(com.meolist.core.R.string.core_delete))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
