package com.meolist.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.durationText
import com.meolist.core.ui.charts.ChartBar
import com.meolist.core.ui.charts.ChartSlice
import com.meolist.core.ui.charts.DonutChart
import com.meolist.core.ui.charts.StackedBarChart
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.stats.StatsBucket
import com.meolist.domain.stats.StatsPeriod
import com.meolist.domain.stats.StatsRange
import com.meolist.domain.stats.TimeStats
import com.meolist.domain.stats.TimeStatsCalculator
import com.meolist.domain.task.TaskRepository
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.TimeRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs
import kotlin.math.roundToInt

data class StatsState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val range: StatsRange = StatsRange(StatsPeriod.WEEK, LocalDate.now(), LocalDate.now()),
    val today: LocalDate = LocalDate.now(),
    val stats: TimeStats? = null,
    val previousTotalMs: Long = 0,
    val completedTasks: Int = 0,
    val types: List<ActivityType> = emptyList(),
    val selectedType: Long? = null,
    val firstDayOfWeek: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY,
)

private data class StatsInput(val period: StatsPeriod, val anchor: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val records: TimeRecordRepository,
    private val types: ActivityTypeRepository,
    private val tasks: TaskRepository,
    private val prefs: AppPreferences,
    private val clock: Clock,
) : ViewModel() {
    private val input = MutableStateFlow(StatsInput(StatsPeriod.WEEK, clock.today()))
    private val selected = MutableStateFlow<Long?>(null)

    /** Keeps running timers' shares fresh while the screen is open. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(clock.now())
            delay(30_000)
        }
    }

    private data class Window(
        val input: StatsInput,
        val range: StatsRange,
        val stats: TimeStats,
        val previousTotalMs: Long,
        val completedTasks: Int,
        val types: List<ActivityType>,
        val firstDayOfWeek: java.time.DayOfWeek,
    )

    private val window: Flow<Window> = combine(input, prefs.settings) { i, settings ->
        i to settings.firstDayOfWeek
    }.distinctUntilChanged().flatMapLatest { (i, firstDay) ->
        val range = TimeStatsCalculator.range(i.period, i.anchor, firstDay)
        val previous = TimeStatsCalculator.shift(range, -1, firstDay)
        val zone = clock.zone()
        val (from, to) = TimeStatsCalculator.millis(range, zone)
        val (prevFrom, prevTo) = TimeStatsCalculator.millis(previous, zone)
        combine(
            records.observeRecords(from, to),
            records.observeRunning(),
            records.observeRecords(prevFrom, prevTo),
            types.observeTypes(),
            tasks.observeAll(),
        ) { current, running, prev, allTypes, allTasks ->
            val now = clock.now()
            val today = clock.today()
            Window(
                input = i,
                range = range,
                stats = TimeStatsCalculator.compute(range, current, running, now, zone, today),
                previousTotalMs = TimeStatsCalculator.compute(previous, prev, running, now, zone, today).totalMs,
                completedTasks = allTasks.count { t ->
                    t.isDone && t.completedAt?.let { it >= from && it < to } == true
                },
                types = allTypes,
                firstDayOfWeek = firstDay,
            )
        }.combine(ticker) { w, _ -> w }
    }

    val state: StateFlow<StatsState> = combine(window, selected) { w, selectedType ->
        StatsState(
            period = w.input.period,
            range = w.range,
            today = clock.today(),
            stats = w.stats,
            previousTotalMs = w.previousTotalMs,
            completedTasks = w.completedTasks,
            types = w.types,
            selectedType = selectedType?.takeIf { id -> w.types.any { it.id == id } },
            firstDayOfWeek = w.firstDayOfWeek,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun now(): Long = clock.now()

    fun setPeriod(period: StatsPeriod) {
        input.value = input.value.copy(period = period, anchor = clock.today())
        selected.value = null
    }

    fun move(steps: Long) {
        val current = state.value
        val shifted = TimeStatsCalculator.shift(current.range, steps, current.firstDayOfWeek)
        // Never move past the period that contains today.
        if (shifted.start > current.today) return
        input.value = input.value.copy(anchor = shifted.start)
        selected.value = null
    }

    fun backToToday() {
        input.value = input.value.copy(anchor = clock.today())
        selected.value = null
    }

    fun selectType(id: Long?) {
        selected.value = if (id == selected.value) null else id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsRoute(onBack: () -> Unit, vm: StatsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var selectedBar by rememberSaveable { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meolist.core.R.string.core_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatsPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = state.period == period,
                        onClick = { vm.setPeriod(period); selectedBar = -1 },
                        shape = SegmentedButtonDefaults.itemShape(index, StatsPeriod.entries.size),
                    ) {
                        Text(stringResource(periodLabel(period)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            val isCurrent = state.today in state.range
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                IconButton(onClick = { vm.move(-1); selectedBar = -1 }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.stats_previous))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = !isCurrent) { vm.backToToday() }
                        .padding(6.dp),
                ) {
                    Text(rangeTitle(state.range, locale), style = MaterialTheme.typography.titleMedium)
                    if (!isCurrent) {
                        Text(
                            stringResource(R.string.stats_back_to_now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = { vm.move(1); selectedBar = -1 }, enabled = !isCurrent) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.stats_next))
                }
            }

            val stats = state.stats
            val types = state.types.associateBy { it.id }
            if (stats == null || stats.totalMs <= 0) {
                EmptyStats(Modifier.padding(vertical = 48.dp))
            } else {
                SummaryCard(state)
                Spacer(Modifier.height(12.dp))
                PieCard(state, stats, types, vm)
                Spacer(Modifier.height(12.dp))
                BarCard(
                    state = state,
                    stats = stats,
                    types = types,
                    selectedBar = selectedBar,
                    onSelectBar = { selectedBar = it ?: -1 },
                    locale = locale,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(state: StatsState) {
    val stats = state.stats ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(durationText(stats.totalMs), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                ChangeBadge(current = stats.totalMs, previous = state.previousTotalMs)
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(previousLabel(state.period)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                SummaryValue(stringResource(R.string.stats_daily_average), durationText(stats.dailyAverageMs), Modifier.weight(1f))
                SummaryValue(stringResource(R.string.stats_active_days), stats.activeDays.toString(), Modifier.weight(1f))
                SummaryValue(
                    stringResource(R.string.stats_tasks_done),
                    state.completedTasks.toString(),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChangeBadge(current: Long, previous: Long) {
    val ratio = TimeStatsCalculator.changeRatio(current, previous) ?: return
    val up = ratio >= 0
    val percent = (abs(ratio) * 100).roundToInt()
    val color = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Text(
        (if (up) "▲ " else "▼ ") + "$percent%",
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun PieCard(
    state: StatsState,
    stats: TimeStats,
    types: Map<Long, ActivityType>,
    vm: StatsViewModel,
) {
    val total = stats.totalMs
    val slices = stats.byType.map { totalOf ->
        ChartSlice(
            key = totalOf.typeId,
            value = totalOf.ms.toFloat(),
            color = types[totalOf.typeId]?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline,
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.stats_by_activity), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    slices = slices,
                    selectedKey = state.selectedType,
                    onSelect = vm::selectType,
                    description = stringResource(R.string.stats_pie_desc),
                    modifier = Modifier.width(210.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.stats_total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(durationText(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            stats.byType.forEach { totalOf ->
                val type = types[totalOf.typeId]
                val percent = (totalOf.ms * 100.0 / total).roundToInt()
                val selected = state.selectedType == totalOf.typeId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { vm.selectType(totalOf.typeId) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(type?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        type?.name ?: stringResource(R.string.stats_unknown_activity),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End,
                    )
                    Text(
                        durationText(totalOf.ms),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarCard(
    state: StatsState,
    stats: TimeStats,
    types: Map<Long, ActivityType>,
    selectedBar: Int,
    onSelectBar: (Int?) -> Unit,
    locale: java.util.Locale,
) {
    val selectedType = state.selectedType
    fun StatsBucket.value(): Long =
        if (selectedType == null) totalMs else perType[selectedType] ?: 0
    val visibleMax = stats.buckets.maxOfOrNull { it.value() } ?: 0
    val bars = stats.buckets.map { bucket ->
        val segments = bucket.perType
            .filterValues { it > 0 }
            .toList()
            .sortedBy { (id, _) -> types[id]?.sortOrder ?: Long.MAX_VALUE }
            .filter { selectedType == null || it.first == selectedType }
            .map { (id, ms) ->
                ChartSlice(id, ms.toFloat(), types[id]?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline)
            }
        ChartBar(segments, bucketLabel(bucket, state.period, locale))
    }
    val title = when (state.period) {
        StatsPeriod.DAY -> R.string.stats_by_hour
        StatsPeriod.WEEK, StatsPeriod.MONTH -> R.string.stats_by_day
        StatsPeriod.YEAR -> R.string.stats_by_month
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (selectedType != null) {
                    Text(
                        types[selectedType]?.name.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelectBar(null) }
                            .padding(4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StackedBarChart(
                bars = bars,
                maxLabel = durationText(visibleMax),
                selectedIndex = selectedBar.takeIf { it in bars.indices },
                onSelect = onSelectBar,
                description = stringResource(R.string.stats_bar_desc),
            )
            val info = bars.getOrNull(selectedBar)
            if (info != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(bucketRangeLabel(stats, selectedBar, locale), durationText(info.total.toLong()))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EmptyStats(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.stats_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun periodLabel(period: StatsPeriod): Int = when (period) {
    StatsPeriod.DAY -> R.string.stats_period_day
    StatsPeriod.WEEK -> R.string.stats_period_week
    StatsPeriod.MONTH -> R.string.stats_period_month
    StatsPeriod.YEAR -> R.string.stats_period_year
}

private fun previousLabel(period: StatsPeriod): Int = when (period) {
    StatsPeriod.DAY -> R.string.stats_previous_day
    StatsPeriod.WEEK -> R.string.stats_previous_week
    StatsPeriod.MONTH -> R.string.stats_previous_month
    StatsPeriod.YEAR -> R.string.stats_previous_year
}

@Composable
private fun rangeTitle(range: StatsRange, locale: java.util.Locale): String {
    val formatter = { pattern: String, date: LocalDate ->
        java.time.format.DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(locale, pattern),
            locale,
        ).format(date)
    }
    return when (range.period) {
        StatsPeriod.DAY -> formatter("yMMMdEEE", range.start)
        StatsPeriod.WEEK -> {
            val last = range.endExclusive.minusDays(1)
            formatter("MMMd", range.start) + " – " + formatter("MMMd", last)
        }
        StatsPeriod.MONTH -> formatter("yMMMM", range.start)
        StatsPeriod.YEAR -> range.start.year.toString()
    }
}

private fun bucketLabel(bucket: StatsBucket, period: StatsPeriod, locale: java.util.Locale): String? {
    val hour = bucket.hour
    return when {
        hour != null -> if (hour % 3 == 0) hour.toString() else null
        period == StatsPeriod.YEAR ->
            if (bucket.date.monthValue % 2 == 1) bucket.date.month.getDisplayName(TextStyle.SHORT, locale) else null
        else -> {
            val day = bucket.date.dayOfMonth
            if (day == 1 || day % 5 == 0) day.toString() else null
        }
    }
}

@Composable
private fun bucketRangeLabel(stats: TimeStats, index: Int, locale: java.util.Locale): String? {
    val bucket = stats.buckets.getOrNull(index) ?: return null
    val formatter = java.time.format.DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"),
        locale,
    )
    val hour = bucket.hour
    return when {
        hour != null -> "%02d:00–%02d:00".format(hour, (hour + 1) % 24)
        stats.range.period == StatsPeriod.YEAR -> bucket.date.month.getDisplayName(TextStyle.FULL, locale)
        else -> formatter.format(bucket.date)
    }
}
