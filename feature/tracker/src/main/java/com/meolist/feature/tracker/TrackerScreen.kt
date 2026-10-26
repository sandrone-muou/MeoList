package com.meolist.feature.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meolist.core.theme.Meo
import com.meolist.core.theme.tabular
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoPalette
import com.meolist.core.ui.MeoTimePickerDialog
import com.meolist.core.ui.accentColor
import com.meolist.core.ui.components.EmptyState
import com.meolist.core.ui.components.MeoProgressBar
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.components.rememberConfirmHaptic
import com.meolist.core.ui.durationText
import com.meolist.core.ui.readableOn
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

    MeoScaffold(
        title = stringResource(R.string.tracker_title),
        large = true,
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
                contentPadding = PaddingValues(
                    start = Meo.spacing.screen,
                    end = Meo.spacing.screen,
                    top = Meo.spacing.xs,
                    bottom = Meo.spacing.bottomContent,
                ),
                horizontalArrangement = Arrangement.spacedBy(Meo.spacing.md),
                verticalArrangement = Arrangement.spacedBy(Meo.spacing.md),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                val runningCards = state.cards.filter { it.running != null }
                if (runningCards.size > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RunningHero(
                            cards = runningCards,
                            now = now,
                            onStop = { vm.toggle(it) },
                            onStopAll = vm::stopAll,
                            onAdjust = { editingRunning = it },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(state.cards, key = { it.type.id }) { card ->
                    TypeCardView(
                        card = card,
                        windows = state.windows,
                        now = now,
                        modifier = Modifier.animateItem(),
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
    modifier: Modifier = Modifier,
) {
    val color = accentColor(card.type.color)
    val running = card.running
    var menu by remember { mutableStateOf(false) }
    val haptic = rememberConfirmHaptic()
    val pulse = rememberPulseAlpha()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "trackerButtonScale",
    )

    fun periodMs(period: GoalPeriod): Long {
        val window = windows[period] ?: return 0
        val live = running?.let { TrackerTime.overlapMs(it.startedAt, now, window.from, window.to) } ?: 0
        return (card.recordedMs[period] ?: 0) + live
    }

    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (running != null) {
                        Modifier.border(1.dp, color.copy(alpha = 0.26f + 0.22f * pulse), MaterialTheme.shapes.large)
                    } else Modifier,
                ),
        ) {
            Column(Modifier.padding(Meo.spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RunningIcon(icon = card.type.icon, color = color, running = running != null)
                    Spacer(Modifier.width(Meo.spacing.sm))
                    Text(
                        card.type.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            stringResource(com.meolist.core.R.string.core_more_options),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(Meo.spacing.sm))
                Text(
                    if (running != null) {
                        elapsedText(now - running.startedAt)
                    } else {
                        durationText(periodMs(GoalPeriod.DAY))
                    },
                    style = MaterialTheme.typography.headlineSmall.tabular(),
                    color = if (running != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    if (running != null) {
                        card.runningTaskTitle
                            ?: running.comment.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.tracker_today_total, durationText(periodMs(GoalPeriod.DAY)))
                    } else {
                        stringResource(R.string.tracker_today_label)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                card.goal?.let { goal ->
                    val done = periodMs(goal.period)
                    val target = goal.targetSec * 1000
                    Spacer(Modifier.height(Meo.spacing.sm))
                    MeoProgressBar(
                        progress = (done.toFloat() / target).coerceIn(0f, 1f),
                        color = color,
                        height = 6.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(goalLabel(goal.period), durationText(done), durationText(target)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (done >= target) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Meo.spacing.md))
                if (running != null) {
                    FilledTonalButton(
                        onClick = {
                            haptic()
                            onToggle()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        interactionSource = interaction,
                        modifier = Modifier.heightIn(min = 44.dp).scale(buttonScale),
                    ) {
                        Icon(Icons.Outlined.StopCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tracker_stop))
                    }
                } else {
                    Button(
                        onClick = {
                            haptic()
                            onToggle()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        interactionSource = interaction,
                        modifier = Modifier.heightIn(min = 44.dp).scale(buttonScale),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tracker_start))
                    }
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

@Composable
private fun RunningHero(
    cards: List<TypeCard>,
    now: Long,
    onStop: (TypeCard) -> Unit,
    onStopAll: () -> Unit,
    onAdjust: (TypeCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberPulseAlpha()
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.fillMaxWidth().padding(Meo.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(color = MaterialTheme.colorScheme.primary, pulse = pulse)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.tracker_running_count, cards.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(visible = cards.size > 1) {
                    TextButton(
                        onClick = onStopAll,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(R.string.tracker_stop_all))
                    }
                }
            }
            Spacer(Modifier.height(Meo.spacing.sm))
            if (cards.size <= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
                    cards.forEach { card ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 4 },
                            modifier = Modifier.weight(1f),
                        ) {
                            RunningTile(
                                card = card,
                                now = now,
                                onStop = onStop,
                                onAdjust = onAdjust,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
                    items(cards, key = { it.type.id }) { card ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 4 },
                        ) {
                            RunningTile(
                                card = card,
                                now = now,
                                onStop = onStop,
                                onAdjust = onAdjust,
                                modifier = Modifier.width(172.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningTile(
    card: TypeCard,
    now: Long,
    onStop: (TypeCard) -> Unit,
    onAdjust: (TypeCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = card.running ?: return
    val haptic = rememberConfirmHaptic()
    val pulse = rememberPulseAlpha()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tileScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "runningTileScale",
    )
    Row(
        modifier
            .scale(tileScale)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) { onAdjust(card) }
            .padding(start = Meo.spacing.md, end = 4.dp, top = Meo.spacing.sm, bottom = Meo.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(color = accentColor(card.type.color), pulse = pulse)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.type.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(
                targetState = elapsedText(now - running.startedAt),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "runningElapsed",
            ) { elapsed ->
                Text(
                    elapsed,
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    maxLines = 1,
                )
            }
        }
        IconButton(
            onClick = {
                haptic()
                onStop(card)
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Outlined.StopCircle,
                stringResource(R.string.tracker_stop),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RunningIcon(icon: String, color: Color, running: Boolean) {
    val pulse = rememberPulseAlpha()
    val spin = rememberInfiniteTransition(label = "trackerSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "trackerSpinAngle",
    )
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (running) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.06f + 0.05f * pulse)),
            )
            Canvas(Modifier.size(46.dp)) {
                val inset = 2.dp.toPx()
                drawArc(
                    color = color.copy(alpha = 0.65f),
                    startAngle = angle,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ActivityIcons.of(icon), null, tint = color.readableOn(), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun rememberPulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "trackerPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trackerPulseAlpha",
    )
    return pulse
}

@Composable
private fun PulseDot(color: Color, pulse: Float) {
    Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(12.dp)
                .scale(0.7f + pulse * 0.3f)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f * pulse)),
        )
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
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
    EmptyState(
        icon = Icons.Outlined.Timer,
        title = stringResource(R.string.tracker_empty_title),
        body = stringResource(R.string.tracker_empty_text),
        modifier = modifier.fillMaxSize(),
        primary = stringResource(R.string.tracker_add_suggested) to { onAddSuggested(suggestions) },
        secondary = stringResource(R.string.tracker_new_type) to onAddType,
    )
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
                        Icon(ActivityIcons.of(type.icon), null, tint = accentColor(type.color), modifier = Modifier.size(20.dp))
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
