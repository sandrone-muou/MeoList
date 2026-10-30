package com.meolist.feature.stats

import com.meolist.core.ui.components.MeoScaffold
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.theme.Meo
import com.meolist.core.ui.accentColor
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.durationText
import com.meolist.core.ui.longDate
import com.meolist.core.ui.readableOn
import com.meolist.core.ui.charts.ChartBar
import com.meolist.core.ui.charts.ChartSlice
import com.meolist.core.ui.charts.DonutChart
import com.meolist.core.ui.charts.donutDiameterForCenter
import com.meolist.core.ui.charts.StackedBarChart
import androidx.compose.material3.CircularProgressIndicator
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.settings.MonthChartStyle
import com.meolist.domain.settings.StatsDefaultView
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
    val monthChartStyle: MonthChartStyle = MonthChartStyle.HEATMAP,
    val isLoading: Boolean = true,
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
    private val input = MutableStateFlow<StatsInput?>(null)
    private val selected = MutableStateFlow<Long?>(null)
    private val periodWrites = Channel<StatsPeriod>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            val settings = prefs.settings.first()
            val period = when (settings.statsDefaultView) {
                StatsDefaultView.DAY -> StatsPeriod.DAY
                StatsDefaultView.WEEK -> StatsPeriod.WEEK
                StatsDefaultView.MONTH -> StatsPeriod.MONTH
                StatsDefaultView.YEAR -> StatsPeriod.YEAR
                StatsDefaultView.LAST_USED -> settings.lastStatsPeriod
            }
            input.value = StatsInput(period, clock.today())
            for (chosen in periodWrites) {
                prefs.updateSettings { it.copy(lastStatsPeriod = chosen) }
            }
        }
    }

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
    )

    private val window: Flow<Window> = combine(input.filterNotNull(), prefs.settings) { i, settings ->
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
            )
        }.combine(ticker) { w, _ -> w }
    }

    val state: StateFlow<StatsState> = combine(
        window.onStart<Window?> { emit(null) }, selected, input, prefs.settings,
    ) { window, selectedType, current, settings ->
        if (current == null) return@combine StatsState()
        val range = TimeStatsCalculator.range(current.period, current.anchor, settings.firstDayOfWeek)
        val loaded = window?.takeIf { it.input == current && it.range == range }
        StatsState(
            period = current.period,
            range = range,
            today = clock.today(),
            stats = loaded?.stats,
            previousTotalMs = loaded?.previousTotalMs ?: 0,
            completedTasks = loaded?.completedTasks ?: 0,
            types = loaded?.types.orEmpty(),
            selectedType = selectedType?.takeIf { id -> loaded == null || loaded.types.any { it.id == id } },
            firstDayOfWeek = settings.firstDayOfWeek,
            monthChartStyle = settings.monthChartStyle,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun now(): Long = clock.now()

    fun setPeriod(period: StatsPeriod) {
        val current = input.value ?: return
        input.value = current.copy(period = period, anchor = clock.today())
        selected.value = null
        periodWrites.trySend(period)
    }

    fun move(steps: Long) {
        val current = input.value ?: return
        val firstDay = state.value.firstDayOfWeek
        val range = TimeStatsCalculator.range(current.period, current.anchor, firstDay)
        val shifted = TimeStatsCalculator.shift(range, steps, firstDay)
        if (shifted.start > clock.today()) return
        input.value = current.copy(anchor = shifted.start)
        selected.value = null
    }

    fun backToToday() {
        val current = input.value ?: return
        input.value = current.copy(anchor = clock.today())
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
    var selectedDay by remember(state.range.start) { mutableStateOf<LocalDate?>(null) }

    MeoScaffold(
        title = stringResource(R.string.stats_title),
        onBack = onBack,
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@MeoScaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = com.meolist.core.theme.Meo.spacing.screen),
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
            if (stats == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (stats.totalMs <= 0) {
                EmptyStats(Modifier.padding(vertical = 48.dp))
            } else {
                SummaryCard(state)
                Spacer(Modifier.height(Meo.layout.listGap))
                PieCard(state, stats, types, vm)
                Spacer(Modifier.height(Meo.layout.listGap))
                if (state.period == StatsPeriod.MONTH && state.monthChartStyle == MonthChartStyle.HEATMAP) {
                    MonthHeatmapCard(
                        state = state,
                        stats = stats,
                        types = types,
                        selectedDay = selectedDay,
                        onSelectDay = { selectedDay = it },
                        locale = locale,
                    )
                } else {
                    BarCard(
                        state = state,
                        stats = stats,
                        types = types,
                        selectedBar = selectedBar,
                        onSelectBar = { selectedBar = it ?: -1 },
                        locale = locale,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(state: StatsState) {
    val stats = state.stats ?: return
    val layout = Meo.layout
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = layout.cardPadH, vertical = layout.cardPadV)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    durationText(stats.totalMs),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Bottom),
                )
                ChangeBadge(current = stats.totalMs, previous = state.previousTotalMs)
                Text(
                    stringResource(previousLabel(state.period)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Bottom),
                )
            }
            Spacer(Modifier.height(layout.listGap))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 8.dp
                if (summaryColumns(maxWidth.value, LocalDensity.current.fontScale) == 3) {
                    Row(verticalAlignment = Alignment.Top) {
                        SummaryValue(stringResource(R.string.stats_daily_average), durationText(stats.dailyAverageMs), Modifier.weight(1f))
                        SummaryDivider()
                        SummaryValue(stringResource(R.string.stats_active_days), stats.activeDays.toString(), Modifier.weight(1f))
                        SummaryDivider()
                        SummaryValue(stringResource(R.string.stats_tasks_done), state.completedTasks.toString(), Modifier.weight(1f))
                    }
                } else {
                    val itemWidth = (maxWidth - gap) / 2
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        SummaryValue(stringResource(R.string.stats_daily_average), durationText(stats.dailyAverageMs), Modifier.width(itemWidth))
                        SummaryValue(stringResource(R.string.stats_active_days), stats.activeDays.toString(), Modifier.width(itemWidth))
                        SummaryValue(stringResource(R.string.stats_tasks_done), state.completedTasks.toString(), Modifier.width(itemWidth))
                    }
                }
            }
        }
    }
}

internal fun summaryColumns(widthDp: Float, fontScale: Float): Int =
    if (widthDp >= 300f * fontScale.coerceAtLeast(1f)) 3 else 2

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun SummaryDivider() {
    VerticalDivider(
        modifier = Modifier.height(24.dp).padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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

internal fun statsDurationGroups(ms: Long, hourSuffix: String, minuteSuffix: String): List<String> {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60L
    return buildList {
        if (hours > 0L) add("$hours$hourSuffix")
        if (hours == 0L || minutes % 60L != 0L) add("${minutes % 60L}$minuteSuffix")
    }
}

@Composable
private fun PieCard(
    state: StatsState,
    stats: TimeStats,
    types: Map<Long, ActivityType>,
    vm: StatsViewModel,
) {
    val groups = statsDurationGroups(
        stats.totalMs, stringResource(R.string.stats_hour_short), stringResource(R.string.stats_minute_short),
    )
    val totalLabel = stringResource(R.string.stats_total)
    val fullDescription = "$totalLabel: ${durationText(stats.totalMs)}"
    val durationStyle = MaterialTheme.typography.titleSmall.copy(
        fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
    )
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val measuredGroups = groups.map { measurer.measure(it, durationStyle, softWrap = false).size }
    val measuredLabel = measurer.measure(totalLabel, labelStyle, softWrap = false).size
    val contentSide = with(density) {
        maxOf(
            measuredGroups.maxOf { it.width }, measuredLabel.width,
            measuredGroups.sumOf { it.height } + measuredLabel.height + 2.dp.roundToPx(),
        ).toDp() + 2.dp
    }
    val thickness = 20.dp
    val centerPadding = 6.dp
    val requiredDiameter = donutDiameterForCenter(contentSide.value, thickness.value, centerPadding.value).dp
    val legendWidth = with(density) {
        val nameWidth = stats.byType.take(4).maxOfOrNull {
            measurer.measure(types[it.typeId]?.name.orEmpty().take(8), MaterialTheme.typography.bodySmall).size.width
        } ?: 0
        maxOf(88.dp * fontScale, nameWidth.toDp()) + 24.dp
    }
    val slices = stats.byType.map { totalOf ->
        ChartSlice(
            key = totalOf.typeId,
            value = totalOf.ms.toFloat(),
            color = types[totalOf.typeId]?.let { accentColor(it.color) } ?: MaterialTheme.colorScheme.outline,
        )
    }
    val totalContent: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics { contentDescription = fullDescription },
        ) {
            groups.forEach { group ->
                Text(group, style = durationStyle, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(2.dp))
            Text(totalLabel, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = Meo.layout.cardPadH, vertical = Meo.layout.cardPadV)) {
            Text(stringResource(R.string.stats_by_activity), style = MaterialTheme.typography.titleSmall)
            if (state.selectedType != null) {
                Text(
                    types[state.selectedType]?.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { vm.selectType(null) }
                        .padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val centerFits = requiredDiameter <= maxWidth
                val diameter = maxOf(Meo.layout.donutMin, requiredDiameter).coerceAtMost(maxWidth)
                val ring: @Composable () -> Unit = {
                    DonutChart(
                        slices = slices,
                        selectedKey = state.selectedType,
                        onSelect = vm::selectType,
                        description = stringResource(R.string.stats_pie_desc),
                        thickness = thickness,
                        centerPadding = centerPadding,
                        modifier = Modifier.size(diameter),
                        center = { if (centerFits) totalContent() },
                    )
                }
                if (centerFits && diameter + 16.dp + legendWidth <= maxWidth) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ring()
                        Spacer(Modifier.width(16.dp))
                        ActivityLegend(stats, types, state.selectedType, vm::selectType, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ring()
                        if (!centerFits) {
                            Spacer(Modifier.height(8.dp))
                            totalContent()
                        }
                        Spacer(Modifier.height(8.dp))
                        ActivityLegend(stats, types, state.selectedType, vm::selectType, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLegend(
    stats: TimeStats,
    types: Map<Long, ActivityType>,
    selectedType: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        stats.byType.take(4).forEach { totalOf ->
            val type = types[totalOf.typeId]
            val percent = (totalOf.ms * 100.0 / stats.totalMs).roundToInt()
            val selected = selectedType == totalOf.typeId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(totalOf.typeId) }
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(type?.let { accentColor(it.color) } ?: MaterialTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        type?.name ?: stringResource(R.string.stats_unknown_activity),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$percent% · ${durationText(totalOf.ms)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (stats.byType.size > 4) {
            Text(
                "+${stats.byType.size - 4}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
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
                ChartSlice(id, ms.toFloat(), types[id]?.let { accentColor(it.color) } ?: MaterialTheme.colorScheme.outline)
            }
        ChartBar(segments, bucketLabel(bucket, state.period, locale))
    }
    val title = when (state.period) {
        StatsPeriod.DAY -> R.string.stats_by_hour
        StatsPeriod.WEEK, StatsPeriod.MONTH -> R.string.stats_by_day
        StatsPeriod.YEAR -> R.string.stats_by_month
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = Meo.layout.cardPadH, vertical = Meo.layout.cardPadV)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                if (selectedType != null) {
                    Text(
                        types[selectedType]?.name.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelectBar(null) }
                            .padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            StackedBarChart(
                bars = bars,
                maxLabel = durationText(visibleMax),
                selectedIndex = selectedBar.takeIf { it in bars.indices },
                onSelect = onSelectBar,
                description = stringResource(R.string.stats_bar_desc),
                height = Meo.layout.barChartHeight,
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
private fun MonthHeatmapCard(
    state: StatsState,
    stats: TimeStats,
    types: Map<Long, ActivityType>,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate?) -> Unit,
    locale: java.util.Locale,
) {
    val selectedType = state.selectedType
    val accent = selectedType?.let { types[it] }?.let { accentColor(it.color) } ?: MaterialTheme.colorScheme.primary
    val emptyCell = MaterialTheme.colorScheme.surfaceContainerHigh
    val values = stats.buckets.associate { bucket ->
        bucket.date to if (selectedType == null) bucket.totalMs else bucket.perType[selectedType] ?: 0L
    }
    val max = values.values.maxOrNull() ?: 0L
    fun levelOf(ms: Long): Int = when {
        ms <= 0L || max <= 0L -> 0
        ms * 4 <= max -> 1
        ms * 2 <= max -> 2
        ms * 4 <= max * 3 -> 3
        else -> 4
    }
    val shades = floatArrayOf(0f, 0.32f, 0.55f, 0.78f, 1f)
    fun shade(level: Int): Color = lerp(emptyCell, accent, shades[level])

    val leading = (stats.range.start.dayOfWeek.value - state.firstDayOfWeek.value + 7) % 7
    val days = stats.range.days
    val weekRows = (leading + days + 6) / 7
    val weekdayLabels = (0 until 7).map {
        state.firstDayOfWeek.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale)
    }
    val cellHeight = Meo.layout.heatmapCell
    val gap = Meo.layout.heatmapGap

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = Meo.layout.cardPadH, vertical = Meo.layout.cardPadV)) {
            Text(stringResource(R.string.stats_by_day), style = MaterialTheme.typography.titleSmall)
            if (selectedType != null) {
                Text(
                    types[selectedType]?.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.stats_heatmap_less),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .width(42.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    emptyCell,
                                    lerp(emptyCell, accent, 0.35f),
                                    lerp(emptyCell, accent, 0.7f),
                                    accent,
                                ),
                            ),
                        ),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    stringResource(R.string.stats_heatmap_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                weekdayLabels.forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(weekRows) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(7) { column ->
                            val index = row * 7 + column - leading
                            if (index !in 0 until days) {
                                Spacer(Modifier.weight(1f).height(cellHeight))
                            } else {
                                val date = stats.range.start.plusDays(index.toLong())
                                val level = levelOf(values[date] ?: 0L)
                                val selected = selectedDay == date
                                val isToday = date == state.today
                                val background by animateColorAsState(
                                    targetValue = if (level == 0) emptyCell else shade(level),
                                    animationSpec = tween(320),
                                    label = "heatmapCell",
                                )
                                val cellScale by animateFloatAsState(
                                    targetValue = if (selected) 1.05f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                    label = "heatmapCellScale",
                                )
                                val shape = RoundedCornerShape(8.dp)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(cellHeight)
                                        .scale(cellScale)
                                        .clip(shape)
                                        .background(background)
                                        .then(
                                            when {
                                                selected -> Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    shape,
                                                )
                                                isToday -> Modifier.border(
                                                    1.5.dp,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                    shape,
                                                )
                                                else -> Modifier
                                            },
                                        )
                                        .clickable { onSelectDay(if (selected) null else date) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = if (isToday || selected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = when {
                                            level >= 3 -> accent.readableOn()
                                            level == 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val day = selectedDay
                if (day != null) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(8.dp))
                    Text(longDate(day, locale), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        durationText(values[day] ?: 0L),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        stringResource(R.string.stats_heatmap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        period == StatsPeriod.WEEK -> bucket.date.dayOfMonth.toString()
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
