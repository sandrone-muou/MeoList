package com.meolist.feature.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.ActivityIcons
import com.meolist.core.ui.ColorPicker
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoIconPicker
import com.meolist.core.ui.MeoPalette
import com.meolist.core.ui.durationText
import com.meolist.domain.common.ApplicationScope
import com.meolist.domain.tracker.ActivityGoal
import com.meolist.domain.tracker.ActivityType
import com.meolist.domain.tracker.ActivityTypeRepository
import com.meolist.domain.tracker.GoalPeriod
import com.meolist.domain.tracker.TimerInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TypeEditState(
    val loaded: Boolean = false,
    val type: ActivityType = ActivityType(name = "", icon = ActivityIcons.DEFAULT, color = MeoPalette.default),
    /** Target seconds per period; absent means no goal. */
    val goals: Map<GoalPeriod, Long> = emptyMap(),
    val isNew: Boolean = true,
)

@HiltViewModel
class ActivityTypeEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val types: ActivityTypeRepository,
    private val timers: TimerInteractor,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val typeId: Long = savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0

    private val _state = MutableStateFlow(TypeEditState())
    val state: StateFlow<TypeEditState> = _state

    init {
        viewModelScope.launch {
            if (typeId == 0L) {
                val count = types.getTypes().size
                _state.value = TypeEditState(
                    loaded = true,
                    type = ActivityType(name = "", icon = ActivityIcons.DEFAULT, color = MeoPalette.colors[count % MeoPalette.colors.size]),
                )
            } else {
                val type = types.getType(typeId)
                val goals = types.getGoals().filter { it.typeId == typeId }
                _state.value = if (type == null) {
                    TypeEditState(loaded = true)
                } else {
                    TypeEditState(loaded = true, type = type, goals = goals.associate { it.period to it.targetSec }, isNew = false)
                }
            }
        }
    }

    fun update(transform: (ActivityType) -> ActivityType) {
        _state.value = _state.value.let { it.copy(type = transform(it.type)) }
    }

    fun setGoal(period: GoalPeriod, targetSec: Long?) {
        _state.value = _state.value.let { s ->
            s.copy(goals = if (targetSec == null || targetSec <= 0) s.goals - period else s.goals + (period to targetSec))
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.type.name.isBlank()) return
        appScope.launch {
            timers.saveType(s.type, s.goals.map { (period, sec) -> ActivityGoal(s.type.id, period, sec) })
        }
        onDone()
    }

    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        if (typeId == 0L) return
        appScope.launch { timers.setArchived(typeId, archived) }
        onDone()
    }

    fun delete(onDone: () -> Unit) {
        if (typeId == 0L) return
        appScope.launch { timers.deleteType(typeId) }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityTypeEditRoute(onBack: () -> Unit, vm: ActivityTypeEditViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<GoalPeriod?>(null) }
    var nameError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.tracker_new_type else R.string.tracker_edit_type)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meolist.core.R.string.core_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        nameError = state.type.name.isBlank()
                        vm.save(onBack)
                    }) {
                        Icon(Icons.Outlined.Check, stringResource(com.meolist.core.R.string.core_save))
                    }
                    if (!state.isNew) {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(com.meolist.core.R.string.core_more_options))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (state.type.archived) R.string.tracker_unarchive else R.string.tracker_archive)) },
                                    onClick = { menu = false; vm.setArchived(!state.type.archived, onBack) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(com.meolist.core.R.string.core_delete)) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val color = Color(state.type.color)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(ActivityIcons.of(state.type.icon), null, tint = Color.White)
                }
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = state.type.name,
                    onValueChange = { name -> nameError = false; vm.update { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.tracker_type_name)) },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SectionTitle(stringResource(R.string.tracker_type_color))
            ColorPicker(selected = state.type.color, onSelect = { c -> vm.update { it.copy(color = c) } })
            SectionTitle(stringResource(R.string.tracker_type_icon))
            MeoIconPicker(
                selected = state.type.icon,
                tint = color,
                onSelect = { key -> vm.update { it.copy(icon = key) } },
            )
            SectionTitle(stringResource(R.string.tracker_goals))
            Text(
                stringResource(R.string.tracker_goals_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GoalPeriod.entries.forEach { period ->
                val target = state.goals[period]
                ListItem(
                    headlineContent = { Text(stringResource(periodName(period))) },
                    trailingContent = {
                        Text(
                            target?.let { durationText(it * 1000) } ?: stringResource(R.string.tracker_goal_none),
                            color = if (target != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { editingGoal = period },
                )
                HorizontalDivider()
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editingGoal?.let { period ->
        GoalDialog(
            period = period,
            initialSec = state.goals[period],
            onConfirm = { vm.setGoal(period, it) },
            onDismiss = { editingGoal = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.tracker_delete_type_title, state.type.name),
            text = stringResource(R.string.tracker_delete_type_text),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

internal fun periodName(period: GoalPeriod): Int = when (period) {
    GoalPeriod.DAY -> R.string.tracker_period_day
    GoalPeriod.WEEK -> R.string.tracker_period_week
    GoalPeriod.MONTH -> R.string.tracker_period_month
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

/** Picks a goal as hours and minutes; clearing removes it. */
@Composable
private fun GoalDialog(period: GoalPeriod, initialSec: Long?, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    var hours by remember { mutableStateOf(initialSec?.let { (it / 3600).toString() } ?: "") }
    var minutes by remember { mutableStateOf(initialSec?.let { ((it % 3600) / 60).toString() } ?: "") }
    val total = (hours.toLongOrNull() ?: 0) * 3600 + (minutes.toLongOrNull() ?: 0) * 60
    val valid = total in 60..(if (period == GoalPeriod.DAY) 24 * 3600L else 31 * 24 * 3600L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracker_goal_dialog_title, stringResource(periodName(period)))) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.tracker_hours)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.tracker_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(total); onDismiss() }, enabled = valid) {
                Text(stringResource(com.meolist.core.R.string.core_ok))
            }
        },
        dismissButton = {
            if (initialSec != null) {
                TextButton(onClick = { onConfirm(null); onDismiss() }) { Text(stringResource(com.meolist.core.R.string.core_clear)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
            }
        },
    )
}
