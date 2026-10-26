package com.meolist.feature.tasks.quadrant

import com.meolist.core.ui.components.MeoScaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.meolist.feature.tasks.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.relativeDateLabel
import com.meolist.core.ui.timeLabel
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.settings.AppPreferences
import com.meolist.domain.task.Priority
import com.meolist.domain.task.Quadrant
import com.meolist.domain.task.QuadrantRules
import com.meolist.domain.task.Task
import com.meolist.domain.task.TaskInteractor
import com.meolist.domain.task.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuadrantState(
    val byQuadrant: Map<Quadrant, List<Task>> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val urgentDays: Int = 1,
    val loaded: Boolean = false,
)

@HiltViewModel
class QuadrantViewModel @Inject constructor(
    tasks: TaskRepository,
    private val interactor: TaskInteractor,
    prefs: AppPreferences,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<QuadrantState> = combine(tasks.observeAll(), prefs.settings) { all, settings ->
        val today = clock.today()
        val zone = clock.zone()
        val open = all.filter { !it.isDone && !it.isDeleted }
        QuadrantState(
            byQuadrant = Quadrant.entries.associateWith { q ->
                open.filter { QuadrantRules.of(it, today, settings.urgentDays, zone) == q }
                    .sortedWith(compareBy({ it.dueAt ?: Long.MAX_VALUE }, { it.sortOrder }))
            },
            today = today,
            urgentDays = settings.urgentDays,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuadrantState())

    /** Dropping changes only the priority, never the due date, so urgency stays date-driven. */
    fun move(task: Task, quadrant: Quadrant) {
        val priority = QuadrantRules.priorityFor(quadrant)
        if (task.priority == priority) return
        appScope.launch { interactor.save(task.copy(priority = priority)) }
    }

    fun toggle(task: Task) = appScope.launch { interactor.setDone(task.id, !task.isDone) }
}

private data class QuadrantStyle(
    val label: Int,
    val icon: ImageVector,
    val tint: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuadrantRoute(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    vm: QuadrantViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bounds = remember { mutableStateMapOf<Quadrant, Rect>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragTask by remember { mutableStateOf<Task?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var hovered by remember { mutableStateOf<Quadrant?>(null) }
    val previewWidth = 180.dp
    val density = LocalDensity.current

    fun quadrantAt(position: Offset): Quadrant? =
        bounds.entries.firstOrNull { it.value.contains(position) }?.key

    fun resetDrag() {
        dragTask = null
        hovered = null
    }

    MeoScaffold(
        title = stringResource(R.string.quadrant_title),
        onBack = onBack,
    ) { padding ->
        if (!state.loaded) return@MeoScaffold
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .onGloballyPositioned { rootOrigin = it.boundsInWindow().topLeft },
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Text(
                    stringResource(
                        R.string.quadrant_hint,
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.quadrant_urgent_window,
                            state.urgentDays,
                            state.urgentDays,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    QuadrantCard(
                        quadrant = Quadrant.URGENT_IMPORTANT,
                        tasks = state.byQuadrant[Quadrant.URGENT_IMPORTANT].orEmpty(),
                        style = QuadrantStyle(
                            R.string.quadrant_do,
                            Icons.Outlined.PriorityHigh,
                            MaterialTheme.colorScheme.error,
                        ),
                        today = state.today,
                        hovered = hovered,
                        dragTask = dragTask,
                        onBounds = { bounds[Quadrant.URGENT_IMPORTANT] = it },
                        onOpenTask = onOpenTask,
                        onToggle = vm::toggle,
                        onDragStart = { task, position -> dragTask = task; dragPosition = position; hovered = quadrantAt(position) },
                        onDrag = { amount ->
                            dragPosition += amount
                            hovered = quadrantAt(dragPosition)
                        },
                        onDragEnd = {
                            val target = hovered
                            val task = dragTask
                            if (target != null && task != null) vm.move(task, target)
                            resetDrag()
                        },
                        onDragCancel = { resetDrag() },
                        modifier = Modifier.weight(1f).fillMaxSize().padding(4.dp),
                    )
                    QuadrantCard(
                        quadrant = Quadrant.NOT_URGENT_IMPORTANT,
                        tasks = state.byQuadrant[Quadrant.NOT_URGENT_IMPORTANT].orEmpty(),
                        style = QuadrantStyle(
                            R.string.quadrant_schedule,
                            Icons.Outlined.Event,
                            MaterialTheme.colorScheme.primary,
                        ),
                        today = state.today,
                        hovered = hovered,
                        dragTask = dragTask,
                        onBounds = { bounds[Quadrant.NOT_URGENT_IMPORTANT] = it },
                        onOpenTask = onOpenTask,
                        onToggle = vm::toggle,
                        onDragStart = { task, position -> dragTask = task; dragPosition = position; hovered = quadrantAt(position) },
                        onDrag = { amount ->
                            dragPosition += amount
                            hovered = quadrantAt(dragPosition)
                        },
                        onDragEnd = {
                            val target = hovered
                            val task = dragTask
                            if (target != null && task != null) vm.move(task, target)
                            resetDrag()
                        },
                        onDragCancel = { resetDrag() },
                        modifier = Modifier.weight(1f).fillMaxSize().padding(4.dp),
                    )
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    QuadrantCard(
                        quadrant = Quadrant.URGENT_NOT_IMPORTANT,
                        tasks = state.byQuadrant[Quadrant.URGENT_NOT_IMPORTANT].orEmpty(),
                        style = QuadrantStyle(
                            R.string.quadrant_delegate,
                            Icons.Outlined.People,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                        today = state.today,
                        hovered = hovered,
                        dragTask = dragTask,
                        onBounds = { bounds[Quadrant.URGENT_NOT_IMPORTANT] = it },
                        onOpenTask = onOpenTask,
                        onToggle = vm::toggle,
                        onDragStart = { task, position -> dragTask = task; dragPosition = position; hovered = quadrantAt(position) },
                        onDrag = { amount ->
                            dragPosition += amount
                            hovered = quadrantAt(dragPosition)
                        },
                        onDragEnd = {
                            val target = hovered
                            val task = dragTask
                            if (target != null && task != null) vm.move(task, target)
                            resetDrag()
                        },
                        onDragCancel = { resetDrag() },
                        modifier = Modifier.weight(1f).fillMaxSize().padding(4.dp),
                    )
                    QuadrantCard(
                        quadrant = Quadrant.NOT_URGENT_NOT_IMPORTANT,
                        tasks = state.byQuadrant[Quadrant.NOT_URGENT_NOT_IMPORTANT].orEmpty(),
                        style = QuadrantStyle(
                            R.string.quadrant_later,
                            Icons.Outlined.Schedule,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        today = state.today,
                        hovered = hovered,
                        dragTask = dragTask,
                        onBounds = { bounds[Quadrant.NOT_URGENT_NOT_IMPORTANT] = it },
                        onOpenTask = onOpenTask,
                        onToggle = vm::toggle,
                        onDragStart = { task, position -> dragTask = task; dragPosition = position; hovered = quadrantAt(position) },
                        onDrag = { amount ->
                            dragPosition += amount
                            hovered = quadrantAt(dragPosition)
                        },
                        onDragEnd = {
                            val target = hovered
                            val task = dragTask
                            if (target != null && task != null) vm.move(task, target)
                            resetDrag()
                        },
                        onDragCancel = { resetDrag() },
                        modifier = Modifier.weight(1f).fillMaxSize().padding(4.dp),
                    )
                }
            }

            dragTask?.let { task ->
                val offsetX = with(density) { (dragPosition.x - rootOrigin.x - previewWidth.toPx() / 2).toInt() }
                val offsetY = with(density) { (dragPosition.y - rootOrigin.y - 24.dp.toPx()).toInt() }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .width(previewWidth)
                        .zIndex(10f),
                ) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuadrantCard(
    quadrant: Quadrant,
    tasks: List<Task>,
    style: QuadrantStyle,
    today: LocalDate,
    hovered: Quadrant?,
    dragTask: Task?,
    onBounds: (Rect) -> Unit,
    onOpenTask: (Long) -> Unit,
    onToggle: (Task) -> Unit,
    onDragStart: (Task, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTarget = hovered == quadrant && dragTask != null
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .then(
                if (isTarget) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else Modifier,
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
            ) {
                Icon(style.icon, null, tint = style.tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(style.label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tasks.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.quadrant_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            today = today,
                            onOpen = { onOpenTask(task.id) },
                            onToggle = { onToggle(task) },
                            onDragStart = { position -> onDragStart(task, position) },
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    today: LocalDate,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    val dueDate = task.dueAt?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    val overdue = dueDate != null && dueDate.isBefore(today)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowOrigin = it.boundsInWindow().topLeft }
            .pointerInput(task.id, task.dueAt) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position -> onDragStart(rowOrigin + position) },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
            .padding(end = 4.dp),
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(32.dp),
        )
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            task.dueAt?.let { due ->
                DueLabel(due, task.allDay, today, overdue)
            }
        }
    }
}

@Composable
private fun DueLabel(due: Long, allDay: Boolean, today: LocalDate, overdue: Boolean) {
    val zone = ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
    val text = if (allDay) {
        relativeDateLabel(date, today)
    } else {
        relativeDateLabel(date, today) + " " + timeLabel(java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalTime())
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
