package com.meolist.feature.countdown

import com.meolist.core.ui.components.EmptyState
import com.meolist.core.ui.components.MeoScaffold
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.accentColor
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.longDate
import com.meolist.core.ui.readableOn
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.common.Clock
import com.meolist.domain.common.today
import com.meolist.domain.countdown.Countdown
import com.meolist.domain.countdown.CountdownInteractor
import com.meolist.domain.countdown.CountdownMath
import com.meolist.domain.countdown.CountdownMode
import com.meolist.domain.countdown.CountdownRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CountdownItem(
    val countdown: Countdown,
    val target: LocalDate,
    val days: Long,
    val years: Int,
)

data class CountdownsState(
    val items: List<CountdownItem> = emptyList(),
    val archived: List<Countdown> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val loaded: Boolean = false,
)

@HiltViewModel
class CountdownsViewModel @Inject constructor(
    private val countdowns: CountdownRepository,
    private val interactor: CountdownInteractor,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<CountdownsState> = combine(
        countdowns.observeAll(),
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(clock.now())
                kotlinx.coroutines.delay(60_000)
            }
        },
    ) { all, _ ->
        val today = clock.today()
        val items = all.filter { !it.archived }.map { c ->
            CountdownItem(
                countdown = c,
                target = CountdownMath.target(c, today),
                days = when (c.mode) {
                    CountdownMode.DOWN -> CountdownMath.daysToTarget(c, today)
                    CountdownMode.UP -> CountdownMath.daysSince(c, today)
                },
                years = CountdownMath.yearsSince(c, today),
            )
        }.sortedWith(
            compareByDescending<CountdownItem> { it.countdown.pinned }
                .thenBy { item -> if (item.countdown.mode == CountdownMode.DOWN && item.days < 0) 1 else 0 }
                .thenBy { it.days },
        )
        CountdownsState(
            items = items,
            archived = all.filter { it.archived },
            today = today,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountdownsState())

    fun setPinned(item: CountdownItem) = appScope.launch {
        interactor.setPinned(item.countdown.id, !item.countdown.pinned)
    }

    fun setArchived(countdown: Countdown, archived: Boolean) = appScope.launch {
        interactor.setArchived(countdown.id, archived)
    }

    fun delete(countdown: Countdown) = appScope.launch { interactor.delete(countdown.id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownsRoute(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: CountdownsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Countdown?>(null) }

    MeoScaffold(
        title = stringResource(R.string.countdown_title),
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(com.meolist.core.R.string.core_more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.countdown_archived_count, state.archived.size)) },
                        enabled = state.archived.isNotEmpty(),
                        onClick = { menu = false; showArchived = true },
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.items.isNotEmpty()) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.countdown_new))
                }
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Unit
            state.items.isEmpty() -> EmptyCountdowns(onAdd = onAdd, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                items(state.items, key = { it.countdown.id }) { item ->
                    CountdownCard(
                        item = item,
                        onOpen = { onOpen(item.countdown.id) },
                        onPin = { vm.setPinned(item) },
                        onArchive = { vm.setArchived(item.countdown, true) },
                        onDelete = { deleting = item.countdown },
                    )
                }
            }
        }
    }

    deleting?.let { countdown ->
        ConfirmDialog(
            title = stringResource(R.string.countdown_delete_title, countdown.title),
            text = stringResource(R.string.countdown_delete_text),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { vm.delete(countdown) },
            onDismiss = { deleting = null },
        )
    }
    if (showArchived) {
        ArchivedCountdownsDialog(
            countdowns = state.archived,
            onRestore = { vm.setArchived(it, false) },
            onDismiss = { showArchived = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CountdownCard(
    item: CountdownItem,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = item.countdown
    val color = accentColor(c.color)
    val locale = currentLocale()
    var menu by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.HourglassBottom, null, tint = color.readableOn(), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (c.yearly) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.countdown_yearly),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        buildString {
                            // A count-up shows the milestone it started from, never a future anniversary.
                            append(longDate(if (c.mode == CountdownMode.UP) c.baseDay else item.target, locale))
                            if (c.mode == CountdownMode.UP && c.yearly && item.years > 0) {
                                append(" · ")
                                append(stringResource(R.string.countdown_years_since, item.years))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    when {
                        c.mode == CountdownMode.DOWN && item.days == 0L -> Text(
                            stringResource(R.string.countdown_today),
                            style = MaterialTheme.typography.titleMedium,
                            color = color,
                        )
                        else -> Text(
                            if (c.mode == CountdownMode.DOWN && item.days < 0) (-item.days).toString() else item.days.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                        )
                    }
                    Text(
                        when {
                            c.mode == CountdownMode.UP -> stringResource(R.string.countdown_days_label)
                            item.days < 0 -> stringResource(R.string.countdown_days_ago)
                            item.days == 0L -> stringResource(R.string.countdown_today_label)
                            else -> stringResource(R.string.countdown_days_left)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (c.pinned) {
                    Icon(
                        Icons.Outlined.PushPin,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (c.pinned) R.string.countdown_unpin else R.string.countdown_pin)) },
                onClick = { menu = false; onPin() },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.countdown_archive)) }, onClick = { menu = false; onArchive() })
            DropdownMenuItem(text = { Text(stringResource(com.meolist.core.R.string.core_delete)) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun EmptyCountdowns(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Outlined.HourglassBottom,
            title = stringResource(R.string.countdown_empty_title),
            body = stringResource(R.string.countdown_empty_text),
            primary = stringResource(R.string.countdown_new) to onAdd,
        )
    }
}

@Composable
private fun ArchivedCountdownsDialog(
    countdowns: List<Countdown>,
    onRestore: (Countdown) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.countdown_archived_title)) },
        text = {
            Column {
                countdowns.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(accentColor(c.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRestore(c) }) { Text(stringResource(R.string.countdown_unarchive)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_ok)) } },
    )
}
