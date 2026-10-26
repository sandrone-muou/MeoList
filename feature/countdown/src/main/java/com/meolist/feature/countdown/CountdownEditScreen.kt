package com.meolist.feature.countdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meolist.core.navigation.Routes
import com.meolist.core.ui.ColorPicker
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.MeoDatePickerDialog
import com.meolist.core.ui.MeoPalette
import com.meolist.core.ui.components.MeoScaffold
import com.meolist.core.ui.components.rememberBackGuard
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.longDate
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CountdownEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val countdown: Countdown = Countdown(title = "", baseDay = LocalDate.now()),
    /** Snapshot at load time; any difference means unsaved edits. */
    val original: Countdown? = null,
    val titleError: Boolean = false,
)

@HiltViewModel
class CountdownEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val countdowns: CountdownRepository,
    private val interactor: CountdownInteractor,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val id: Long = savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0
    private val _state = MutableStateFlow(CountdownEditState())
    val state: StateFlow<CountdownEditState> = _state

    init {
        viewModelScope.launch {
            _state.value = if (id == 0L) {
                val fresh = Countdown(
                    title = "",
                    baseDay = clock.today().plusDays(30),
                    color = MeoPalette.colors[2],
                )
                CountdownEditState(loaded = true, countdown = fresh, original = fresh)
            } else {
                val existing = countdowns.get(id)
                if (existing == null) {
                    CountdownEditState(loaded = true, isNew = false)
                } else {
                    CountdownEditState(loaded = true, isNew = false, countdown = existing, original = existing)
                }
            }
        }
    }

    fun update(transform: (Countdown) -> Countdown) {
        _state.value = _state.value.let { it.copy(countdown = transform(it.countdown), titleError = false) }
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.countdown.title.isBlank()) {
            _state.value = s.copy(titleError = true)
            return
        }
        appScope.launch { interactor.save(s.countdown) }
        onSaved()
    }

    fun delete(onDeleted: () -> Unit) {
        if (id == 0L) return
        appScope.launch { interactor.delete(id) }
        onDeleted()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownEditRoute(onBack: () -> Unit, vm: CountdownEditViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = state.countdown
    val locale = currentLocale()
    val today = LocalDate.now()
    val preview = when {
        c.mode == CountdownMode.DOWN -> {
            val days = CountdownMath.daysToTarget(c, today)
            when {
                days > 0 -> stringResource(R.string.countdown_preview_left, days)
                days == 0L -> stringResource(R.string.countdown_preview_today)
                else -> stringResource(R.string.countdown_preview_past, -days)
            }
        }
        else -> stringResource(R.string.countdown_preview_since, CountdownMath.daysSince(c, today))
    }

    val dirty = state.original != null && c != state.original
    val guard = rememberBackGuard(dirty = dirty, onBack = onBack)

    MeoScaffold(
        title = stringResource(if (state.isNew) R.string.countdown_new else R.string.countdown_edit),
        onBack = guard.request,
        actions = {
            if (!state.isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(com.meolist.core.R.string.core_delete))
                }
            }
            IconButton(onClick = { vm.save(onBack) }, enabled = state.loaded) {
                Icon(Icons.Outlined.Check, stringResource(com.meolist.core.R.string.core_save))
            }
        },
    ) { padding ->
        if (!state.loaded) return@MeoScaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = com.meolist.core.theme.Meo.spacing.screen),
        ) {
            OutlinedTextField(
                value = c.title,
                onValueChange = { title -> vm.update { it.copy(title = title) } },
                label = { Text(stringResource(R.string.countdown_name)) },
                placeholder = { Text(stringResource(R.string.countdown_name_hint)) },
                isError = state.titleError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionTitle(stringResource(R.string.countdown_date))
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(longDate(c.baseDay, locale))
            }
            Text(
                preview,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionTitle(stringResource(R.string.countdown_mode))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CountdownMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = c.mode == mode,
                        onClick = { vm.update { it.copy(mode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, CountdownMode.entries.size),
                    ) { Text(stringResource(if (mode == CountdownMode.DOWN) R.string.countdown_mode_down else R.string.countdown_mode_up)) }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.countdown_yearly_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.countdown_yearly_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = c.yearly, onCheckedChange = { yearly -> vm.update { it.copy(yearly = yearly) } })
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.countdown_pin_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.countdown_pin_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = c.pinned, onCheckedChange = { pinned -> vm.update { it.copy(pinned = pinned) } })
            }

            SectionTitle(stringResource(R.string.countdown_color))
            ColorPicker(selected = c.color, onSelect = { color -> vm.update { it.copy(color = color) } })

            SectionTitle(stringResource(R.string.countdown_note))
            OutlinedTextField(
                value = c.note,
                onValueChange = { note -> vm.update { it.copy(note = note) } },
                placeholder = { Text(stringResource(R.string.countdown_note_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate) {
        MeoDatePickerDialog(
            initial = c.baseDay,
            onConfirm = { date -> vm.update { it.copy(baseDay = date) } },
            onDismiss = { pickDate = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.countdown_delete_title, c.title),
            text = stringResource(R.string.countdown_delete_text),
            confirmLabel = stringResource(com.meolist.core.R.string.core_delete),
            onConfirm = { vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
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
