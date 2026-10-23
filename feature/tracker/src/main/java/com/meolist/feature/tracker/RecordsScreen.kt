package com.meolist.feature.tracker

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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.meolist.core.ui.MeoDatePickerDialog
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.durationText
import com.meolist.core.ui.longDate
import com.meolist.core.ui.relativeDateLabel
import com.meolist.core.ui.timeLabel
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.startOfDayMillis
import com.meolist.domain.common.toLocalDateTime
import com.meolist.domain.common.today
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
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

data class RecordsState(
    val day: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val rows: List<RecordRow> = emptyList(),
    /** Time of the day covered by records (parts outside the day excluded). */
    val totalMs: Long = 0,
)

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

    private val dayRecords = day.flatMapLatest { d ->
        val zone = clock.zone()
        records.observeRecords(d.startOfDayMillis(zone), d.plusDays(1).startOfDayMillis(zone))
    }.mapLatest { list ->
        val titles = list.mapNotNull { it.taskId }.distinct().mapNotNull { id -> tasks.get(id)?.let { id to it.title } }.toMap()
        list to titles
    }

    val state: StateFlow<RecordsState> = combine(day, dayRecords, types.observeTypes()) { d, (list, titles), allTypes ->
        val zone = clock.zone()
        val typeMap = allTypes.associateBy { it.id }
        val from = d.startOfDayMillis(zone)
        val to = d.plusDays(1).startOfDayMillis(zone)
        RecordsState(
            day = d,
            today = clock.today(),
            zone = zone,
            rows = list.map { RecordRow(it, typeMap[it.typeId], it.taskId?.let(titles::get)) },
            totalMs = TrackerTime.totalMs(list, emptyList(), from, to, clock.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsState())

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
fun RecordsRoute(onBack: () -> Unit, vm: RecordsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pickDate by remember { mutableStateOf(false) }
    val deletedMessage = stringResource(R.string.tracker_record_deleted)
    val undoLabel = stringResource(com.meolist.core.R.string.core_undo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tracker_records_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meolist.core.R.string.core_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                        stringResource(R.string.tracker_day_total, durationText(state.totalMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.setDay(state.day.plusDays(1)) }, enabled = state.day < state.today) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.tracker_next_day))
                }
            }
            if (state.rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.tracker_no_records),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.record.id }) { row ->
                        RecordItem(
                            row = row,
                            day = state.day,
                            zone = state.zone,
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

@Composable
private fun RecordItem(row: RecordRow, day: LocalDate, zone: ZoneId, onDelete: () -> Unit) {
    val record = row.record
    val color = row.type?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    val start = record.startAt.toLocalDateTime(zone)
    val end = record.endAt.toLocalDateTime(zone)
    // Mark ends that fall on another day, e.g. a record running past midnight.
    val startText = timeLabel(start.toLocalTime()) + if (start.toLocalDate() != day) " (${start.toLocalDate().dayOfMonth})" else ""
    val endText = timeLabel(end.toLocalTime()) + if (end.toLocalDate() != day) " (${end.toLocalDate().dayOfMonth})" else ""
    val detail = listOfNotNull(row.taskTitle, record.comment.takeIf { it.isNotBlank() }).joinToString(" · ")
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
        modifier = Modifier.fillMaxWidth(),
    )
}
